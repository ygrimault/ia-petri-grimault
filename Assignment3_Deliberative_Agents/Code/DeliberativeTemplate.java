package template;

/* import table */
import com.sun.org.apache.xpath.internal.operations.Bool;
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

import java.util.*;

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

		public int carriedWeight;

		public State(City currentCity, TaskSet availableTasks, TaskSet carriedTasks, TaskSet deliveredTasks){
			this.currentCity = currentCity;
			this.availableTasks = availableTasks;
			this.carriedTasks = carriedTasks;
            this.deliveredTasks = deliveredTasks;

			carriedWeight = this.carriedTasks.weightSum();
		}

		public HashMap<Action, State> subStates(){
			HashMap<Action, State> substates = new HashMap<>();

			for (Task task : carriedTasks) {
				if (task.deliveryCity == currentCity) {
					TaskSet newDeliveredTasks = deliveredTasks.clone();
					TaskSet newCarriedTasks = carriedTasks.clone();
					newDeliveredTasks.add(task);
					newCarriedTasks.remove(task);

					substates.put(new Delivery(task),
							new State(currentCity, availableTasks, newCarriedTasks, newDeliveredTasks));
					return substates;	// If at least one task is delivered, it has to be the optimal choice
				}
			}

			// Now we can see see if we should pickup a task or move
			for (Task task : availableTasks) {
				if (task.pickupCity == currentCity && carriedWeight + task.weight <= capacity) {    // Check if the task is carriable or not
					TaskSet newAvailableTasks = availableTasks.clone();
					TaskSet newCarriedTasks = carriedTasks.clone();
					newAvailableTasks.remove(task);
					newCarriedTasks.add(task);

					substates.put(new Pickup(task),
							new State(currentCity, newAvailableTasks, newCarriedTasks, deliveredTasks));
				}
			}

			for (City city : currentCity.neighbors()) {
				substates.put(new Move(city),
						new State(city, availableTasks, carriedTasks, deliveredTasks));
			}

			return substates;
		}

		@Override
		public int hashCode(){
			return currentCity.hashCode() + 100*availableTasks.hashCode() + 10000*carriedTasks.hashCode() + 1000000*deliveredTasks.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return this.hashCode()==obj.hashCode();
		}
	}

//	private double planCost(Plan plan,Vehicle vehicle, TaskSet deliveredTasks){
//        double distance = plan.totalDistance();
//        double tasksReward = deliveredTasks.rewardSum();
//
//        double totalReward = tasksReward - distance*vehicle.costPerKm();
//
//        return (-1.0)*totalReward;
//    }

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
		this.capacity = vehicle.capacity();

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

		// Create an empty task set with same "universe" of tasks to initialize deliveredTasks in State)
        TaskSet emptyTaskSet=tasks.clone();
        emptyTaskSet.clear();

		// Create initial state
		State curState = new State(current, tasks, vehicle.getCurrentTasks(),emptyTaskSet);

		//// BFS Algorithm on state tree, tree which is updated at each iteration ////

		// We use the same notations as seen in course for Q and C
		ArrayList<PlanToState> Q = new ArrayList<>();		// Stores the plan to go to each state in a queue
		ArrayList<State> C = new ArrayList<>();				// Stores each visited state in order to not visit it a second time

		Q.add(new PlanToState(new Plan(current), curState));	// Initialize Q with initial State and Plan

		while(true){
            if(Q.isEmpty()){
                throw new AssertionError("Should not happen.");
            }
			PlanToState node = Q.remove(0);	// Works like a pop() of a stack
			Plan nodePlan = node.plan;
			State nodeState = node.state;
			if(!C.contains(nodeState)){
				if(nodeState.carriedTasks.isEmpty() && nodeState.availableTasks.isEmpty()){         // This means that we delivered all tasks, ie it is a final state
					return nodePlan;		// For now, we return the first final plan met, which takes the least amount of actions (not necessarily optimal !)
				}

                C.add(nodeState);
                HashMap<Action, State> substates = nodeState.subStates();

                for(Action action : substates.keySet()){
					List<Action> actionList = new ArrayList<>();
					nodePlan.iterator().forEachRemaining(actionList::add);
					Plan newPlan = new Plan(nodeState.currentCity,actionList);
					newPlan.append(action);
					newPlan.seal();
                    Q.add(new PlanToState(newPlan, substates.get(action)));     // Create new plans from existing one and add to END of stack (BFS, not DFS)
                }
            }
		}
	}

	private Plan aStarPlan(Vehicle vehicle, TaskSet tasks){
		City current = vehicle.getCurrentCity();

		// Create an empty task set with same "universe" of tasks to initialize deliveredTasks in State)
		TaskSet emptyTaskSet=tasks.clone();
		emptyTaskSet.clear();

		// Create initial state
		State curState = new State(current, tasks, vehicle.getCurrentTasks(),emptyTaskSet);

        // A* Algorithm on state tree, tree which is updated at each iteration

		// We use the same notations as seen in course for Q and C
		ArrayList<PlanToState> Q = new ArrayList<>();		// Stores the plan to go to each state in a queue
        HashMap<State, Double> C = new HashMap<>();			// Stores each visited state and the current cost of its assigned plan

        Q.add(new PlanToState(new Plan(current), curState));	// Initialize Q with initial State and Plan

        while(true){
            if(Q.isEmpty()){
                throw new AssertionError("Should not happen.");
            }
            PlanToState node = Q.remove(0);	// Works like a pop() of a stack
            Plan nodePlan = node.plan;
            State nodeState = node.state;
            double nodeCost = nodePlan.totalDistance();

			if(!C.containsKey(nodeState) || C.get(nodeState) > nodeCost){
            	if(nodeState.carriedTasks.isEmpty() && nodeState.availableTasks.isEmpty()){
                	return nodePlan;		// We know that this plan is optimal, as it has the lowest total distance, and the total reward is the same
            	}

                C.put(nodeState, nodeCost);
                HashMap<Action, State> substates = nodeState.subStates();
				
                for(Action action : substates.keySet()){
					List<Action> actionList = new ArrayList<>();
					nodePlan.iterator().forEachRemaining(actionList::add);
                    Plan newPlan = new Plan(nodeState.currentCity,actionList);
                    newPlan.append(action);
                    Q.add(new PlanToState(newPlan, substates.get(action)));     // Create new plans from existing one and add to END of stack (BFS, not DFS)
                }
                Collections.sort(Q,(planToState1, planToState2) -> (int)(planToState1.plan.totalDistance() - planToState2.plan.totalDistance())); // Lambda function to compute cost ??? Store cost to avoid multiple computations ?!!
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
