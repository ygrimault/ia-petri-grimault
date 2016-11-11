package auction03;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 *
 *
 */
@SuppressWarnings("unused")
public class Auction03 implements AuctionBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;

    // Our variables
    private List<List<CustomAction>> currentPlan;
    private List<List<CustomAction>> bidPlan;
    private List<Task> wonTasks;


    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;

        long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
        this.random = new Random(seed);
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        if (winner == agent.id()) {
            currentPlan = copyPlan(bidPlan);
            wonTasks.add(previous);
        }
    }

    @Override
    public Long askPrice(Task task) {
        // compute Plan with added task
        // get marginal cost of plan
        // return bid depending on marginal cost
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        return ListToPlan(currentPlan);
    }
}
