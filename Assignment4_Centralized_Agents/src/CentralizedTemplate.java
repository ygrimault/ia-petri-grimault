package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sun.deploy.security.CertificateHostnameVerifier;
import com.sun.org.apache.xml.internal.dtm.ref.DTMDefaultBaseIterators;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import uchicago.src.sim.analysis.DataSource;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private Random random;
    private static final int MAX_ITER = 300000;

    /*
    Class to simulate an action, ie drop or pickup a task
     */
    private class CustomAction{
        public boolean isPickUp;
        public Task task;

        public CustomAction(boolean isPickUp,Task task){
            this.isPickUp = isPickUp;
            this.task = task;
        }

        @Override
        public boolean equals(Object obj) {
            return this.hashCode()==obj.hashCode();
        }

        @Override
        public int hashCode() {
            if (this.isPickUp){
                return 10*task.hashCode()+1;
            } else {
                return 10*task.hashCode();
            }
        }
    }
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.random = new Random();
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        long time_step;
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

        // Initialize The list of "Plans" with all tasks delivered successively by the first agent alone
        List<CustomAction> initialPlanVehicle1 = naivePlan(vehicles.get(0), tasks);
        List<List<CustomAction>> initialPlans = new ArrayList<>();
        initialPlans.add(initialPlanVehicle1);
        while (initialPlans.size() < vehicles.size()) {
            initialPlans.add(new ArrayList<>());
        }

        // Copy the plan to keep track of the best one over all iterations
        List<List<CustomAction>> optimalPlan = copyPlan(initialPlans);

        // Main loop
        for (int i = 0; i < MAX_ITER; i++) {
            update(initialPlans,vehicles,tasks);
            if (totalCost(initialPlans,vehicles) < totalCost(optimalPlan,vehicles)){
                optimalPlan = copyPlan(initialPlans);
            }

            // If we're too close to the end, we need to stop.
            // We also need to be aware of the time necessary to compute the "real" plan.
            time_step = System.currentTimeMillis();
            if(time_step - time_start > timeout_plan - 50){
                break;
            }
        }

        // Compute the "real" list of Plans according to the most optimal solution found
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++){
            List<CustomAction> vehiclePlan = optimalPlan.get(i);
            Vehicle vehicle = vehicles.get(i);
            plans.add(listToPlan(vehiclePlan,vehicle));
        }

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }

    /*
    Implementation of the nextAction function seen in course
    Returns the next action performed after the action passed as argument (by the same vehicle)
     */
    private CustomAction nextAction(List<List<CustomAction>> plan, CustomAction action){
        CustomAction nextAction = null;
        for (int i = 0; i < plan.size(); i++) {
            int id = plan.get(i).indexOf(action);
            if (id!=-1){    // This is a match
                if (id+1<plan.get(i).size()){   // This is not the last action performed (null by default)
                    nextAction = plan.get(i).get(id+1);
                }
                break;
            }
        }
        return nextAction;
    }

    /*
    Implementation of the nextAction function seen in course for vehicles
    Returns the first action performed by the vehicle passed as argument
     */
    private CustomAction nextAction(List<List<CustomAction>> plan, Vehicle vehicle, List<Vehicle> vehicles){
        int id = vehicles.indexOf(vehicle);
        if (plan.get(id).isEmpty()){
            return null;
        } else {
            return plan.get(id).get(0);
        }
    }

    /*
    Implementation of the time function seen in course
    Returns the index of the action passed as argument in the list of actions it's part of
     */
    private int time(List<List<CustomAction>> plan, CustomAction action){
        int time = -1;
        for (List<CustomAction> vehiclePlan : plan){
            time = vehiclePlan.indexOf(action);
            if (time!=-1) {
                return time;
            }
        }
        return time;
    }

    /*
    Implementation of the vehicle function seen in course
    Returns the vehicle that performs the action passed as argument
     */
    private Vehicle vehicle(List<List<CustomAction>> plan, CustomAction action, List<Vehicle> vehicles){
        int vehicleId = -1;     // This should never be returned, except if the action doesn't exist
        for (int i = 0; i < plan.size(); i++) {
            if (plan.get(i).indexOf(action)!=-1){
                vehicleId = i;
                break;
            }
        }
        return vehicles.get(vehicleId);
    }

    /*
    Computes the cost of a plan
     */
    private double totalCost(List<List<CustomAction>> plan, List<Vehicle> vehicles){
        double cost = 0.0;

        for (int i = 0; i < vehicles.size(); i++) {
            int costPerKm = vehicles.get(i).costPerKm();
            List<CustomAction> vehiclePlan = plan.get(i);
            City previousCity = vehicles.get(i).getCurrentCity();
            City nextCity;

            for (CustomAction action : vehiclePlan){
                if (action.isPickUp){
                    nextCity = action.task.pickupCity;
                } else {
                    nextCity = action.task.deliveryCity;
                }

                cost += previousCity.distanceTo(nextCity) * costPerKm;
                previousCity = nextCity;
            }
        }
        return cost;
    }

    /*
    Check whether an action is legally permutable with the next action in the corresponding plan
     */
    private boolean isPermutable(List<List<CustomAction>> plan,CustomAction action,Vehicle vehicle,int vehicleId){
        CustomAction nextAction = nextAction(plan,action);

        // Last action of the list, or the next action is the delivery action corresponding to the pickup action
        if (nextAction == null || nextAction.task == action.task){
            return false;
        }

        // There can be a problem related to capacity iff we try to put a pickup action before a delivery action
        if (!(!action.isPickUp && nextAction.isPickUp)){
            return true;
        }

        int carriedWeight = 0;
        List<CustomAction> vehiclePlan = plan.get(vehicleId);

        // We need to compute the carried weight up until the critical moment
        for (int i = 0; i < vehiclePlan.indexOf(action)-1; i++) {
            CustomAction currentAction = vehiclePlan.get(i);
            if (currentAction.isPickUp){
                carriedWeight+=currentAction.task.weight;
            } else {
                carriedWeight-=currentAction.task.weight;
            }
        }

        if (carriedWeight+nextAction.task.weight > vehicle.capacity()){
            return false;
        } else {
            return true;
        }
    }

    /*
    We choose a task at random, then whether we look at its pickup or its delivery
    Then we choose at random whether we want to entrust this task to another vehicle or permute it (when possible)
        with the next action planned for the corresponding vehicle
     */
    private void update(List<List<CustomAction>> plan, List<Vehicle> vehicles, TaskSet tasks){
        List<Task> tasksList = new ArrayList<>(tasks);
        Task chosenTask = tasksList.get(random.nextInt(tasks.size()));
        CustomAction chosenAction = new CustomAction(random.nextBoolean(),chosenTask);
        Vehicle currentVehicle = vehicle(plan,chosenAction,vehicles);
        int vehicleId = vehicles.indexOf(currentVehicle);

        if (random.nextBoolean() || !isPermutable(plan, chosenAction, currentVehicle, vehicleId)){
            //We choose to change vehicle

            CustomAction complementaryAction = new CustomAction(!chosenAction.isPickUp,chosenTask);

            // We need to choose another random vehicle
            int newVehicleId = random.nextInt(vehicles.size()-1);
            if (newVehicleId >= vehicleId){
                newVehicleId+=1;
            }

            // We first remove the task from the list of actions of the first vehicle
            plan.get(vehicleId).remove(chosenAction);
            plan.get(vehicleId).remove(complementaryAction);

            // Then we add it at the end of the list of actions of the new vehicle, with the pickup first
            if (chosenAction.isPickUp){
                plan.get(newVehicleId).add(chosenAction);
                plan.get(newVehicleId).add(complementaryAction);
            }
            else{
                plan.get(newVehicleId).add(complementaryAction);
                plan.get(newVehicleId).add(chosenAction);
            }
        } else {
            // We permute the chosen action with its successor
            int actionIndex = plan.get(vehicleId).indexOf(chosenAction);
            plan.get(vehicleId).remove(actionIndex);
            plan.get(vehicleId).add(actionIndex+1,chosenAction);

        }
    }

    /*
    Computes the initial list of action where the first vehicle takes one task at a time and delivers them all
     */
    private List<CustomAction> naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        List<CustomAction> plan = new ArrayList<>();

        for (Task task : tasks) {
            plan.add(new CustomAction(true,task));
            plan.add(new CustomAction(false,task));
        }
        return plan;
    }

    private List<List<CustomAction>> copyPlan(List<List<CustomAction>> plans){
        List<List<CustomAction>> copyPlans = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            List<CustomAction> tmpPlan = plans.get(i);
            copyPlans.add(new ArrayList<>());
            for (CustomAction action : tmpPlan) {
                copyPlans.get(i).add(action);
            }
        }
        return copyPlans;
    }

    /*
    Converts a list of CustomAction into a logist Plan
     */
    private Plan listToPlan(List<CustomAction> list, Vehicle vehicle){
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        City nextCity;

        for(CustomAction action : list){
            if(action.isPickUp){
                nextCity = action.task.pickupCity;
                for (City city : current.pathTo(nextCity)){
                    plan.appendMove(city);
                }
                plan.appendPickup(action.task);
                current = nextCity;
            } else {
                nextCity = action.task.deliveryCity;
                for (City city : current.pathTo(nextCity)){
                    plan.appendMove(city);
                }
                plan.appendDelivery(action.task);
                current = nextCity;
            }
        }
        return plan;
    }
}
