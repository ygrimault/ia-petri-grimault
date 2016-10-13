package reactive;

import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

import javax.swing.plaf.ActionMapUIResource;
import java.util.*;

public class ReactiveAgent implements ReactiveBehavior {

	private int numActions;
	private Agent myAgent;
	private Topology topology;

	private List<State> allStates;
	private Map<State, ActionReward> strategy;

	/**
	 * Implements the state. We only need to know the current city, and the destination city of the current task.
	 */
	private class State {
		public City curCity;
		public City curTask;

		public State(City initialCity, City initialTask) {
			curCity = initialCity;
			curTask = initialTask;
		}

		@Override
		public boolean equals(Object state){
			return ((this.curCity == ((State) state).curCity) && (this.curTask == ((State) state).curTask));
		}

		@Override
		public int hashCode(){
			if(curTask != null) {
				return curCity.id * 10000000 + curTask.id;
			} else {
				return curCity.id * 10000000;
			}
		}
	}

	private class ActionReward{
		public int action;
		public double reward;

		public ActionReward(int action, double reward){
			this.action = action;
			this.reward = reward;
		}

		public void addReward(double adding){
			reward += adding;
		}

		@Override
		public boolean equals(Object q){
			return ((this.action == ((ActionReward) q).action) && (this.reward == ((ActionReward) q).reward));
		}
	}

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);
		int totalCities = topology.size();

		allStates = new ArrayList<State>();
		for (City city : topology){
			allStates.add(new State(city, null));
			for (City city2 : topology){
				allStates.add(new State(city, city2));
			}
		}

		strategy = new HashMap<State, ActionReward>();
		for (State s : allStates){
			strategy.put(s, new ActionReward(0, 0.0));
		}
		double test = 5e30;
		int nbIter = 0;
		double precision = 0.01;
		// Initial computation to find best strategy at each point
		while (test > precision && nbIter<100000) {
			Map<State, ActionReward> new_strategy = new HashMap<State, ActionReward>();
			for (State s : allStates){
				ActionReward Q;

				if (s.curTask != null) {
					// First action : Pickup (=-1)

					Q = new ActionReward(-1,(double)td.reward(s.curCity, s.curTask) / (s.curCity.distanceUnitsTo(s.curTask)+1) );
					// Sum part of the formula
					for (City newTask : topology){
                        Q.addReward(discount *
                                td.probability(s.curTask, newTask) *
								strategy.get(new State(s.curTask, newTask)).reward);
                    }
                    // Don't forget the case where there is no task available
                    Q.addReward(discount *
								td.probability(s.curTask, null) *
								strategy.get(new State(s.curTask, null)).reward);
				} else {
					// Make sure other actions have something to compare to
					Q = new ActionReward(-1, -1.0);
				}

				// All other actions : move to city j if it's a neighbor

				for (int j = 0; j < totalCities; j++) {
					City nextCity = topology.cities().get(j);
					if (s.curCity.neighbors().contains(nextCity)) {
						ActionReward tmpQ = new ActionReward(j,0.0);

						// Sum part of the formula (Move actions imply R(s,a) = 0)
						for (City newTask : topology){
							tmpQ.addReward(discount *
                                    td.probability(nextCity, newTask) *
									strategy.get(new State(nextCity, newTask)).reward);
                        }
						// Don't forget the case where there is no task available
						tmpQ.addReward(discount *
                                    td.probability(nextCity, null) *
									strategy.get(new State(nextCity, null)).reward);

						// Check if better reward
						if (tmpQ.reward > Q.reward){
                            Q = tmpQ;
                        }
					}
				}

				new_strategy.put(s, Q);
			}
			test=0;
			for (State s : allStates){
				test+=Math.pow(new_strategy.get(s).reward-strategy.get(s).reward,2);
			}
			test = Math.sqrt(test);
			nbIter++;
			strategy = new_strategy;
		}

		// print the strategy
		for (State s : strategy.keySet()){
			System.out.println("State : " + s.curCity + ", " + s.curTask + ". Value : " + strategy.get(s).action + " : " + strategy.get(s).reward);
		}
		System.out.println(nbIter);
		this.numActions = 0;
		this.myAgent = agent;
		this.topology = topology;
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		ActionReward Q;
		if (availableTask != null) {
			Q = strategy.get(new State(vehicle.getCurrentCity(), availableTask.deliveryCity));
		} else {
			Q = strategy.get(new State(vehicle.getCurrentCity(), null));
		}

		if(Q.action == -1){
			// Pickup action
			action = new Pickup(availableTask);
		} else {
			// Move action
			action = new Move(topology.cities().get(Q.action));
		}

		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;

		return action;
	}
}
