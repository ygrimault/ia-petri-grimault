package auction03;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.Measures;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;


public class Util {
    private static final int MAX_ITER = 100000;
    private static final int MAX_NEIGHS = 100;
    private Random random;
    private long timeout_plan;

    public Util(long timeout_plan){
        this.random = new Random();
        this.timeout_plan = timeout_plan;
    }

    public class CustomAction{
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

    public List<List<CustomAction>> ComputePlan(List<Vehicle> vehicles, List<Task> tasks) {
        long time_start = System.currentTimeMillis();
        long time_step;

        List<List<CustomAction>> nodePlans = naivePlan(tasks, vehicles);

        // Copy the plan to keep track of the best one over all iterations
        List<List<CustomAction>> optimalPlan = copyPlan(nodePlans);
        List<List<CustomAction>> tmpPlan = copyPlan(nodePlans);
        List<List<CustomAction>> neighPlan;

        int nbIterWithNoChange = 0;
        // Main loop
        for (int i = 0; i < MAX_ITER; i++) {
            if (nbIterWithNoChange < 100) {
                nodePlans = copyPlan(optimalPlan);
            }

            // Local exploration to find the "best" neighbour
            for (int j = 0; j < MAX_NEIGHS; j++) {
                neighPlan = copyPlan(nodePlans);
                update(neighPlan, vehicles, tasks);
                if(totalCost(neighPlan, vehicles) < totalCost(tmpPlan, vehicles)){
                    tmpPlan = copyPlan(neighPlan);
                }
            }

            // Move to the best one, even if it doesn't improve
            nodePlans = copyPlan(tmpPlan);

            // We stay at the optimal plan to explore its neighborhood and minimize it
            if (totalCost(nodePlans, vehicles) < totalCost(optimalPlan, vehicles)) {
                optimalPlan = copyPlan(nodePlans);
                nbIterWithNoChange = 0;
            } else {
                nbIterWithNoChange += 1;
            }

            // If we're too close to the end, we need to stop.
            // We also need to be aware of the time necessary to compute the "real" plan.
            time_step = System.currentTimeMillis();
            if(time_step - time_start > timeout_plan - 50){
                break;
            }
        }

        return optimalPlan;
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
    public double totalCost(List<List<CustomAction>> plan, List<Vehicle> vehicles){
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
    Alternate implementation of the update function.
    Now, it moves a random task to a random vehicle at a random position (always with respect to the constraints)
     */
    private void update(List<List<CustomAction>> plan, List<Vehicle> vehicles, List<Task> tasks){
        List<Task> tasksList = new ArrayList<>(tasks);

        // Get a random task, and compute both its pickup action and its delivery action
        Task chosenTask = tasksList.get(random.nextInt(tasks.size()));
        CustomAction pickupAction = new CustomAction(true, chosenTask);
        CustomAction deliveryAction = new CustomAction(false, chosenTask);

        // Remove both actions from current vehicle
        Vehicle currentVehicle = vehicle(plan,pickupAction,vehicles);
        int vehicleId = vehicles.indexOf(currentVehicle);
        plan.get(vehicleId).remove(pickupAction);
        plan.get(vehicleId).remove(deliveryAction);

        // Get a new vehicle for insertion
        int newVehicleId = random.nextInt(vehicles.size());
        Vehicle newVehicle = vehicles.get(newVehicleId);
        List<CustomAction> vehiclePlan = plan.get(newVehicleId);

        // We'll need to explore the plan of the vehicle from start to the chosen position to check the capacity
        int cumulWeight = 0;
        int id = 0;

        int newPickupId = random.nextInt(vehiclePlan.size() + 1);
        boolean pickupTaskPlaced = false;

        while(!pickupTaskPlaced){
            // If it happens that we reach the end of the list, we place the task here
            if(id == vehiclePlan.size()){
                vehiclePlan.add(id, pickupAction);
                break;
            }

            // We need to update the carried weight up until this action
            if(id < newPickupId) {
                CustomAction currentAction = vehiclePlan.get(id);
                if (currentAction.isPickUp) {
                    cumulWeight += currentAction.task.weight;
                } else {
                    cumulWeight -= currentAction.task.weight;
                }
                id += 1;
            }

            // When reaching the chosen position, we check if the vehicle can pickup the task at this point.
            // Otherwise we increment the chosen position to pick it up later
            if(id == newPickupId){
                if (cumulWeight + pickupAction.task.weight < newVehicle.capacity()){
                    vehiclePlan.add(id, pickupAction);
                    pickupTaskPlaced = true;
                } else {
                    newPickupId += 1;
                }
            }
        }

        id = newPickupId;

        // Now that we placed the pick up action, we need to place the delivery action after that, without carrying too much weight in the meantime
        int newDeliveryId = newPickupId + random.nextInt(vehiclePlan.size() - newPickupId) + 1;
        boolean deliveryTaskPlaced = false;

        while(!deliveryTaskPlaced){
            // If it happens that we reach the end of the list, we place the task here
            if(id == vehiclePlan.size()){
                vehiclePlan.add(id, pickupAction);
                break;
            }

            // We need to check if we're not going to carry too much weight, in which case we need to deliver the new task before continuing
            if(id < newDeliveryId){
                CustomAction currentAction = vehiclePlan.get(id);
                if (currentAction.isPickUp) {
                    int weight = currentAction.task.weight;
                    if(cumulWeight + weight > newVehicle.capacity()){
                        vehiclePlan.add(id, deliveryAction);
                        deliveryTaskPlaced = true;
                    } else {
                        cumulWeight += weight;
                    }
                } else {
                    cumulWeight -= currentAction.task.weight;
                }
                id += 1;
            }

            // When we reach the chosen position, there is no drawback to place the delivery action here.
            if(id == newDeliveryId){
                vehiclePlan.add(id, deliveryAction);
                deliveryTaskPlaced = true;
            }
        }
    }

    /*
    Computes the initial plan where the vehicles take the tasks at random and carry them one at a time.
     */
    private List<List<CustomAction>> naivePlan(List<Task> tasks, List<Vehicle> vehicles) {
        List<List<CustomAction>> plan = new ArrayList<>();

        for(Vehicle v : vehicles){
            plan.add(new ArrayList<>());
        }

        int i;
        for (Task task : tasks) {
            i = random.nextInt(vehicles.size());
            plan.get(i).add(new CustomAction(true,task));
            plan.get(i).add(new CustomAction(false,task));
        }
        return plan;
    }

    public List<List<CustomAction>> copyPlan(List<List<CustomAction>> plans){
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
    public Plan listToPlan(List<CustomAction> list, Vehicle vehicle){
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