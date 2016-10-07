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

	private double disFactor;
	private int numActions;
	private Agent myAgent;
	private Topology topology;

	private List<State> allStates;
	private List<Integer> allActions;
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

		for (State s : strategy.keySet()){
			System.out.println("State : " + s.curCity + "," + s.curTask + ". Value : " + strategy.get(s).action + ":" + strategy.get(s).reward);
		}

		// Initial computation to find best strategy at each point
		for (int i = 0; i < 42; i++) {
			Map<State, ActionReward> new_strategy = new HashMap<State, ActionReward>();
			for (State s : allStates){
				ActionReward Q;

				if (s.curTask != null) {
					// First action : Pickup (=-1)

					Q = new ActionReward(-1, (double) td.reward(s.curCity, s.curTask));
					// Sum part of the formula
					for (City newTask : topology){
                        Q.addReward(discount *
                                td.probability(s.curTask, newTask) *
                                strategy.get(allStates.stream()
													.filter(state -> (state.curCity == s.curTask && state.curTask == newTask))
													.findFirst()
													.get())
										.reward);
                    }
                    // Don't forget the case where there is no task available
                    Q.addReward(discount *
								td.probability(s.curTask, null) *
								strategy.get(allStates.stream()
													.filter(state -> (state.curCity == s.curTask && state.curTask == null))
													.findFirst()
													.get())
										.reward);
				} else {
					// Make sure other actions have something to compare to
					Q = new ActionReward(-1, -1.0);
				}

				// All other actions : move to city j if it's a neighboor

				for (int j = 0; j < totalCities; j++) {
					City nextCity = topology.cities().get(j);
					if (s.curCity.neighbors().contains(nextCity)) {
						ActionReward tmpQ = new ActionReward(j, 0.0);

						// Sum part of the formula (Move actions imply R(s,a) = 0)
						for (City newTask : topology){
                            try {
                                tmpQ.addReward(discount *
                                        td.probability(nextCity, newTask) *
                                        strategy.get(allStates.stream()
                                                            .filter(state -> (state.curCity == nextCity && state.curTask == newTask))
                                                            .findFirst()
                                                            .get())
                                                .reward);
                            } catch (NullPointerException e) {
                                System.out.println("curCity : " + nextCity + ". newTask : " + newTask);
                            }
                        }
						// Don't forget the case where there is no task available
						Q.addReward(discount *
                                    td.probability(nextCity, null) *
                                    strategy.get(allStates.stream()
                                                        .filter(state -> (state.curCity == nextCity && state.curTask == null))
                                                        .findFirst()
                                                        .get())
                                            .reward);

						// Check if better reward
						if (tmpQ.reward > Q.reward){
                            Q = tmpQ;
                        }
					}
				}

				new_strategy.put(s, Q);
			}

			strategy = new_strategy;
		}

		// print the strategy
		for (State s : strategy.keySet()){
			System.out.println("State : " + s.curCity + ", " + s.curTask + ". Value : " + strategy.get(s).action + " : " + strategy.get(s).reward);
		}

		this.disFactor = discount;
		this.numActions = 0;
		this.myAgent = agent;
		this.topology = topology;
	}

	public double reward(State state, Action action, TaskDistribution td){
		if(action instanceof Pickup){
			return td.reward(state.curCity, state.curTask);
		} else {
			// We don't take into account the cost of the move
			return 0.0;
		}
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		ActionReward Q;
		if (availableTask != null) {
			Q = strategy.get(allStates.stream()
                                .filter(state -> (state.curCity == vehicle.getCurrentCity() && state.curTask == availableTask.deliveryCity))
                                .findFirst()
                                .get());
		} else {
			Q = strategy.get(allStates.stream()
					.filter(state -> (state.curCity == vehicle.getCurrentCity() && state.curTask == null))
					.findFirst()
					.get());
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
