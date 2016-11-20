package auction03;

//the list of imports
import java.lang.management.MonitorInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import auction03.Util.CustomAction;
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
    private long timeout_plan;
    private long timeout_setup;
    private long timeout_bid;
    private Util util;
    private int maxCapacity = Integer.MIN_VALUE;

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {

        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        // the askPrice method cannot execute more than timeout_plan milliseconds
        timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.util = new Util(timeout_bid, agent.vehicles());
        this.wonTasks = new ArrayList<>();
        this.bidPlan = new ArrayList<>();
        this.currentPlan = new ArrayList<>();

        // Initialize plans
        for (int i = 0; i < agent.vehicles().size(); i++) {
            this.bidPlan.add(new ArrayList<>());
            this.currentPlan.add(new ArrayList<>());
        }

        // Compute the max capacity of our vehicles
        for (Vehicle vehicle : agent.vehicles()){
            if (vehicle.capacity() > this.maxCapacity) {
                this.maxCapacity = vehicle.capacity();
            }
        }

    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        if (winner == agent.id()) {
            currentPlan = util.copyPlan(bidPlan);
            wonTasks.add(previous);

            // Make sure the current plan has the task as provided.
            for (List<CustomAction> vehiclePlan : currentPlan) {
                for (CustomAction action : vehiclePlan) {
                    if (action.task.id == previous.id) {
                        action.task = previous;
                    }
                }
            }
        }
    }

    @Override
    public Long askPrice(Task task) {
        // If none of our vehicles can handle the task, no need to bid for it
        if (task.weight > maxCapacity) {
            return null;
        }
        // compute Plan with added task
        // get marginal cost of plan
        // return bid depending on marginal cost
        wonTasks.add(task);
        bidPlan = util.ComputePlan(currentPlan, task,wonTasks);

        double marginalCost = util.totalCost(bidPlan) - util.totalCost(currentPlan);

        //Adapter le bid pour qu'il soit supérieur au marginalcost pour faire du bénef
        double bid = marginalCost;

        wonTasks.remove(task);

        return (long) Math.round(bid);
    }

    // TODO : modifier en fonction de la version de Vincent
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++){
            List<CustomAction> vehiclePlan = currentPlan.get(i);
            Vehicle vehicle = vehicles.get(i);
            plans.add(util.listToPlan(vehiclePlan,vehicle));
        }

        return plans;
    }
}
