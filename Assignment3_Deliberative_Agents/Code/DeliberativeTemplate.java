package template;

/* import table */
import logist.plan.Action;
import logist.plan.Action.Delivery;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR }

	/* Environment */
	Topology topology;
	TaskDistribution td;

	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;

	private class State{
		public City currentCity;
		public TaskSet availableTasks;
		public TaskSet carriedTasks;
        public TaskSet deliveredTasks;

		public State(City currentCity, TaskSet availableTasks, TaskSet carriedTasks, TaskSet deliveredTasks){
			this.currentCity = currentCity;
			this.availableTasks = availableTasks;
			this.carriedTasks = carriedTasks;
            this.deliveredTasks = deliveredTasks;
		}

		public HashMap<Action, State> subStates(){	// Faire une map avec les actions ?!!
			HashMap<Action, State> substates = new HashMap<>();

            for (Task task : carriedTasks){
                if(task.deliveryCity == currentCity){
                    TaskSet newDeliveredTasks = deliveredTasks.clone();
                    TaskSet newCarriedTasks = carriedTasks.clone();
                    newDeliveredTasks.add(task);
                    newCarriedTasks.remove(task);

                    substates.put(new Pickup(task),
                            new State(currentCity, availableTasks,newCarriedTasks,newDeliveredTasks));
                }
            }

			for(Task task : availableTasks){
				if(task.pickupCity == currentCity){
                    TaskSet newAvailableTasks = availableTasks.clone();
                    TaskSet newCarriedTasks = carriedTasks.clone();
                    newAvailableTasks.remove(task);
                    newCarriedTasks.add(task);

					substates.put(new Pickup(task),
								  new State(currentCity, newAvailableTasks,newCarriedTasks,deliveredTasks));	// Fonction auxiliaire ???
				}
			}

			for(City city : currentCity.neighbors()){
				substates.put(new Move(city),
						  	  new State(city, availableTasks, carriedTasks,deliveredTasks));
			}

			return substates;
		}

		@Override
		public int hashCode(){
			return currentCity.hashCode() + availableTasks.hashCode() + carriedTasks.hashCode();
		}
	}

	private double planCost(Plan plan,Vehicle vehicle, TaskSet deliveredTasks){
        double distance = plan.totalDistance();
        double tasksReward = deliveredTasks.rewardSum();

        double totalReward = tasksReward - distance*vehicle.costPerKm();

        return (-1.0)*totalReward;
    }

	private class PlanToState{
		public Plan plan;
		public State state;

		public PlanToState(Plan plan, State state){
			this.plan = plan;
			this.state = state;
		}
	}

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;

		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");

		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());

		// ...
	}

	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;
        tasks.addAll(vehicle.getCurrentTasks());

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = aStarPlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = bfsPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}
		return plan;
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}

	private Plan bfsPlan(Vehicle vehicle, TaskSet tasks){
		City current = vehicle.getCurrentCity();
        TaskSet emptyTaskSet=tasks.clone();
        emptyTaskSet.clear();
		State curState = new State(current, tasks, vehicle.getCurrentTasks(),emptyTaskSet);

		// BFS Algorithm on state tree, tree which is updated at each iteration
		ArrayList<PlanToState> Q = new ArrayList<PlanToState>();
		HashMap<State, Plan> C = new HashMap<>();

		Q.add(new PlanToState(new Plan(current), curState));

		while(true){
            if(Q.isEmpty()){
                throw new AssertionError("Should not happen.");
            }
			PlanToState node = Q.remove(0);	// Find how to use stacks and their pop function
			Plan nodePlan = node.plan;
			State nodeState = node.state;
			if(nodeState.carriedTasks.isEmpty() && nodeState.availableTasks.isEmpty()){         // Compute a few results and get best one ?
				return nodePlan;
			}
			if(!C.containsKey(nodeState)){
                C.put(nodeState, nodePlan);
                HashMap<Action, State> substates = nodeState.subStates();
                for(Action action : substates.keySet()){
                    Plan newPlan = nodePlan;
                    newPlan.append(action);
                    Q.add(new PlanToState(newPlan, substates.get(action)));     // Create new plans from existing one and add to END of stack (BFS, not DFS)
                }
            }
		}
	}

	private Plan aStarPlan(Vehicle vehicle, TaskSet tasks){
        City current = vehicle.getCurrentCity();
        TaskSet emptyTaskSet=tasks.clone();
        emptyTaskSet.clear();
        State curState = new State(current, tasks, vehicle.getCurrentTasks(),emptyTaskSet);

        // A* Algorithm on state tree, tree which is updated at each iteration
        ArrayList<PlanToState> Q = new ArrayList<PlanToState>();
        HashMap<State, Double> C = new HashMap<>();

        Q.add(new PlanToState(new Plan(current), curState));

        while(true){
            if(Q.isEmpty()){
                throw new AssertionError("Should not happen.");
            }
            PlanToState node = Q.remove(0);	// Find how to use stacks and their pop function
            Plan nodePlan = node.plan;
            State nodeState = node.state;
            double nodeCost = planCost(nodePlan, vehicle,nodeState.deliveredTasks);

            if(nodeState.carriedTasks.isEmpty() && nodeState.availableTasks.isEmpty()){
                return nodePlan;
            }
            if(!C.containsKey(nodeState) || C.get(nodeState) > nodeCost){
                C.put(nodeState, nodeCost);
                HashMap<Action, State> substates = nodeState.subStates();
                for(Action action : substates.keySet()){
                    Plan newPlan = nodePlan;
                    newPlan.append(action);
                    Q.add(new PlanToState(newPlan, substates.get(action)));     // Create new plans from existing one and add to END of stack (BFS, not DFS)
                }
                Q.sort((planToState1, planToState2) -> (int) (planToState1.plan.totalDistance() - planToState2.plan.totalDistance())); // Lambda function to compute cost ??? Store cost to avoid multiple computations ?!!
            }
        }
    }

	@Override
	public void planCancelled(TaskSet carriedTasks) {

		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.

            // Handled directly in plan. Another way would have been to deposit all carried tasks at current city.
		}
	}
}
