package auction03;

//the list of imports
import java.lang.management.MonitorInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.internal.org.objectweb.asm.commons.AdviceAdapter;
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
    private double taskNumber;
    private double opponentRatio;
    private long lastBid;
    private long minBid;

    // Our parameters for the strategy we use
    private double minRatio;
    private double defaultBidRatio;
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
        this.minBid = 2000L;

        this.taskNumber = 0;
        this.opponentRatio = 1;
        this.taskNumber = 0.;

        this.minRatio = 1.05;
        this.defaultBidRatio = 0.75;

        // Initialize plans
        for (int i = 0; i < agent.vehicles().size(); i++) {
            this.bidPlan.add(new ArrayList<>());
            this.currentPlan.add(new ArrayList<>());
        }

        // Compute the max capacity of our vehicles
        for (Vehicle vehicle : agent.vehicles()){
            this.maxCapacity = Math.max(this.maxCapacity, vehicle.capacity());
        }

    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        taskNumber += 1;

        // If we won the bid, we need to update our current status
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

        // For our strategy, we use the opponent that made the best bid (unrelated to the fact that we won it or not)
        long minAdversaryBid = Long.MAX_VALUE;
        for (long adversaryBid : bids) {
            if (adversaryBid > 0 && adversaryBid < minAdversaryBid && adversaryBid != lastBid)
                minAdversaryBid = adversaryBid;
        }

        // We compute the mean of the ratios between our bid and the best of our opponent from start to finish
        if (lastBid != 0) {
            opponentRatio = opponentRatio*(taskNumber-1)/taskNumber + ((double)minAdversaryBid / (double)lastBid)/taskNumber;
        }

        // We store the best bid ever made to never bid below a certain ratio of it
        minBid = Math.min(minBid, minAdversaryBid);
    }

    @Override
    public Long askPrice(Task task) {
        // If none of our vehicles can handle the task, no need to bid for it
        if (task.weight > maxCapacity) {
            return null;
        }

        // Compute Plan with added task
        // Get marginal cost of plan
        // Return bid depending on marginal cost
        wonTasks.add(task);
        bidPlan = util.ComputePlan(currentPlan, task,wonTasks);

        double marginalCost = util.totalCost(bidPlan) - util.totalCost(currentPlan);

        double bid = Math.max(minRatio, opponentRatio) * Math.max(minBid*defaultBidRatio, marginalCost);

        wonTasks.remove(task);

        lastBid = Math.round(bid);
        System.out.println(""+opponentRatio+","+lastBid);
        return lastBid;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++){
            List<CustomAction> vehiclePlan = currentPlan.get(i);
            Vehicle vehicle = vehicles.get(i);
            plans.add(util.listToPlan(vehiclePlan,vehicle));
        }
        System.out.println(util.totalCost(currentPlan));
        System.out.println(tasks.rewardSum());
        return plans;
    }
}
