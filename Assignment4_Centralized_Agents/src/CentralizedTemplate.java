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
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        ArrayList<CustomAction> initialPlanVehicle1 = naivePlan(vehicles.get(0), tasks);
        ArrayList<ArrayList<CustomAction>> initialPlans = new ArrayList<>();
        initialPlans.add(initialPlanVehicle1);
        while (initialPlans.size() < vehicles.size()) {
            initialPlans.add(new ArrayList<>());
        }
        ArrayList<ArrayList<CustomAction>> optimalPlan =(ArrayList<ArrayList<CustomAction>>)initialPlans.clone();
        for (int i = 0; i < 30000; i++) {
            update(initialPlans,vehicles,tasks);
            if (totalCost(initialPlans,vehicles)<totalCost(optimalPlan,vehicles)){
                optimalPlan = (ArrayList<ArrayList<CustomAction>>)initialPlans.clone();
            }
        }
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++){
            ArrayList<CustomAction> vehiclePlan = optimalPlan.get(i);
            Vehicle vehicle = vehicles.get(i);
            plans.add(listToPlan(vehiclePlan,vehicle));
        }
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }

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
            }
            return 10*task.hashCode();
        }
    }

    private int time(ArrayList<ArrayList<CustomAction>> plan, CustomAction action){
        int time = -1;
        for (ArrayList<CustomAction> vehiclePlan : plan){
            time = vehiclePlan.indexOf(action);
            if (time!=-1) {
                return time;
            }
        }
        return time;
    }

    private Vehicle vehicle(ArrayList<ArrayList<CustomAction>> plan, CustomAction action, List<Vehicle> vehicles){
        int vehicleId = -1;
        for (int i = 0; i < plan.size(); i++) {
            if (plan.get(i).indexOf(action)!=-1){
                vehicleId = i;
                break;
            }
        }
        return vehicles.get(vehicleId);
    }

    private CustomAction nextAction(ArrayList<ArrayList<CustomAction>> plan, CustomAction action){
        CustomAction nextAction = null;
        for (int i = 0; i < plan.size(); i++) {
            int id = plan.get(i).indexOf(action);
            if (id!=-1){
                if (id+1<plan.get(i).size()){
                    nextAction = plan.get(i).get(id+1);
                }
                break;
            }
        }
        return nextAction;
    }

    private CustomAction nextAction(ArrayList<ArrayList<CustomAction>> plan, Vehicle vehicle, List<Vehicle> vehicles){
        int id = vehicles.indexOf(vehicle);
        if (plan.get(id).isEmpty()){
            return null;
        }
        return plan.get(id).get(0);
    }

    private double totalCost(ArrayList<ArrayList<CustomAction>> plan,List<Vehicle> vehicles){
        double cost = 0;
        for (int i = 0; i < vehicles.size(); i++) {
            int costPerKm = vehicles.get(i).costPerKm();
            ArrayList<CustomAction> vehiclePlan = plan.get(i);
            City previousCity = vehicles.get(i).getCurrentCity();
            for (CustomAction action : vehiclePlan){
                if (action.isPickUp){
                    cost += previousCity.distanceTo(action.task.pickupCity)*(double)costPerKm;
                    previousCity = action.task.pickupCity;
                }
                else{
                    cost += previousCity.distanceTo(action.task.deliveryCity)*(double)costPerKm;
                    previousCity = action.task.deliveryCity;
                }
            }
        }
        return cost;
    }

    private boolean isPermutable(ArrayList<ArrayList<CustomAction>> plan,CustomAction action,Vehicle vehicle,int vehicleId){
        CustomAction nextAction = nextAction(plan,action);
        if (nextAction == null || nextAction.task == action.task){
            return false;
        }
        if (!(!action.isPickUp && nextAction.isPickUp)){
            return true;
        }
        int carriedWeight = 0;
        ArrayList<CustomAction> vehiclePlan = plan.get(vehicleId);
        for (int i = 0; i < vehiclePlan.indexOf(action)-1; i++) {
            CustomAction currentAction = vehiclePlan.get(i);
            if (currentAction.isPickUp){
                carriedWeight+=currentAction.task.weight;
            }
            else{
                carriedWeight-=currentAction.task.weight;
            }
        }
        if (carriedWeight+nextAction.task.weight > vehicle.capacity()){
            return false;
        }
        return true;

    }

    private void update(ArrayList<ArrayList<CustomAction>> plan, List<Vehicle> vehicles, TaskSet tasks){
        List<Task> tasksList = new ArrayList<>(tasks);
        Task chosenTask = tasksList.get(random.nextInt(tasks.size()));
        CustomAction chosenAction = new CustomAction(random.nextBoolean(),chosenTask);
        Vehicle currentVehicle = vehicle(plan,chosenAction,vehicles);
        int vehicleId = vehicles.indexOf(currentVehicle);
        if (!isPermutable(plan,chosenAction,currentVehicle,vehicleId) || random.nextBoolean()){
            //We choose to change vehicle
            CustomAction complementaryAction = new CustomAction(!chosenAction.isPickUp,chosenTask);
            int newVehicleId = random.nextInt(vehicles.size()-1);
            if (newVehicleId >= vehicleId){
                newVehicleId+=1;
            }
            plan.get(vehicleId).remove(chosenAction);
            plan.get(vehicleId).remove(complementaryAction);
            if (chosenAction.isPickUp){
                plan.get(newVehicleId).add(chosenAction);
                plan.get(newVehicleId).add(complementaryAction);
            }
            else{
                plan.get(newVehicleId).add(complementaryAction);
                plan.get(newVehicleId).add(chosenAction);
            }
        }
        else{
            int actionIndex = plan.get(vehicleId).indexOf(chosenAction);
            plan.get(vehicleId).remove(actionIndex);
            plan.get(vehicleId).add(actionIndex+1,chosenAction);

        }
    }

    private ArrayList<CustomAction> naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        ArrayList<CustomAction> plan = new ArrayList<>();

        for (Task task : tasks) {
            plan.add(new CustomAction(true,task));
            plan.add(new CustomAction(false,task));
        }
        return plan;
    }

    private Plan listToPlan(ArrayList<CustomAction> list,Vehicle vehicle){
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for(CustomAction action : list){
            if(action.isPickUp){
                for (City city : current.pathTo(action.task.pickupCity)){
                    plan.appendMove(city);
                }
                plan.appendPickup(action.task);
                current = action.task.pickupCity;
            }
            else{
                for (City city : current.pathTo(action.task.deliveryCity)){
                    plan.appendMove(city);
                }
                plan.appendDelivery(action.task);
                current = action.task.deliveryCity;
            }
        }
        return plan;
    }
}
