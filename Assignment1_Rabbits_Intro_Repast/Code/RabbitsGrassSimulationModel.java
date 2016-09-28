import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimModelImpl;

// Import due to tutorial
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.gui.Value2DDisplay;
import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.util.SimUtilities;

import java.awt.Color;
import java.util.ArrayList;

/**
 * Class that implements the simulation model for the rabbits grass
 * simulation.  This is the first class which needs to be setup in
 * order to run Repast simulation. It manages the entire RePast
 * environment and the simulation.
 *
 * @author
 */


public class RabbitsGrassSimulationModel extends SimModelImpl {

    // MODIFIED
    /* Schedule object. Might be renamed for clarification */
    private Schedule schedule;

    /* Default values for the parameters */
    private static final int GRIDWIDTH = 20;
    private static final int GRIDHEIGHT = 20;
    private static final int NUMRABBITS = 42;
    private static final int BIRTHRESH = 42;
    private static final int GRASSRATE = 42;

    /* Parameters of the simulation */
    public int gridWidth = GRIDWIDTH;
    public int gridHeight = GRIDHEIGHT;
    public int numRabbits = NUMRABBITS;
    public int birThresh = BIRTHRESH; // Could become a double (or something else) depending on how we implement the energy
    public int grassRate = GRASSRATE; // Could become an int (or something else) depending on how we implement the grass growth rate

    /* The Space object where we run the model */
    private RabbitsGrassSimulationSpace rSpace;

    /* Objects needed for display */
    private DisplaySurface displaySurf;

    /* List of Rabbits agents */
    private ArrayList rabbitList;

    /* Get and Set methods of previous parameters */

    public int getGridWidth() {
        return gridWidth;
    }

    public void setGridWidth(int gridWidth) {
        this.gridWidth = gridWidth;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public void setGridHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }

    public int getNumRabbits() {
        return numRabbits;
    }

    public void setNumRabbits(int numRabbits) {
        this.numRabbits = numRabbits;
    }

    public int getBirThresh() {
        return birThresh;
    }

    public void setBirThresh(int birThresh) {
        this.birThresh = birThresh;
    }

    public int getGrassRate() {
        return grassRate;
    }

    public void setGrassRate(int grassRate) {
        this.grassRate = grassRate;
    }

    public static void main(String[] args) {

        System.out.println("Rabbit skeleton");

        // MODIFIED
        SimInit init = new SimInit();
        RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
        init.loadModel(model, "", false);

    }

    /**
     * Called when "Initialize" button is clicked
     * "Initialize" button -> simulation supposed uninitialized and initialize it
     * Commonly divided into 3 sub-methods
     */
    public void begin() {
        // TODO Auto-generated method stub
        // MODIFIED
        buildModel();
        buildSchedule();
        buildDisplay();

        displaySurf.display();
    }

    public void buildModel() {
        // TODO
        rSpace = new RabbitsGrassSimulationSpace(gridWidth, gridHeight);
        rSpace.plantGrass(grassRate);

        for (int i = 0; i < numRabbits; i++){
            addNewRabbit();
        }
    }

    public void buildSchedule() {
        // TODO
        class RabbitStep extends BasicAction {
            public void execute(){
                SimUtilities.shuffle(rabbitList);
                for(int i = 0; i < rabbitList.size(); i++){
                    RabbitsGrassSimulationAgent rab = (RabbitsGrassSimulationAgent) rabbitList.get(i);
                    // TODO insert actions here, like move, reproduce, die, add grass
                    rab.step();
                    if (rab.getEnergy() < 1){
                        rSpace.removeRabbitAt(rab.getX(), rab.getY());
                        rabbitList.remove(i);
                    }
                }

                rSpace.plantGrass(grassRate);
                displaySurf.updateDisplay();
            }
        }

        schedule.scheduleActionBeginning(0, new RabbitStep());
    }

    public void buildDisplay() {
        // TODO
        ColorMap map = new ColorMap();

        map.mapColor(0, new Color(102, 51, 0));

        // What happens for i >= 16 ?!! We might need to create a function to handle this case
        for(int i = 1; i<16; i++){
            map.mapColor(i, new Color((int) (102. * (15.-(float)i)/15.), (int) (51. * (15.-(float)i)/15. + ((float)i/15.)*127.), 0));
        }

        Value2DDisplay displayGrass = new Value2DDisplay(rSpace.getCurrentGrassSpace(), map);

        Object2DDisplay displayRabbits = new Object2DDisplay(rSpace.getCurrentRabbitSpace());
        displayRabbits.setObjectList(rabbitList);

        displaySurf.addDisplayable(displayGrass, "Grass");
        displaySurf.addDisplayable(displayRabbits, "Agents");
    }

    /**
     * Add 1 rabbit to the list of rabbits. Does NOT place it into the space.
     */
    private void addNewRabbit(){
        RabbitsGrassSimulationAgent r = new RabbitsGrassSimulationAgent();
        rabbitList.add(r);
        rSpace.addRabbit(r);
    }

    /**
     * This returns a list of all parameters that can be modified when running the simulation.
     * The parameters must be named like exampleParam, have get and set methods named getExampleParam and setExampleParam
     *  respectively, and the corresponding string in initParams must be named "ExampleParam"
     * @return List of parameters names
     */
    public String[] getInitParam() {
        // TODO Auto-generated method stub
        // MODIFIED
        String[] initParams = { "GridWidth",
                                "GridHeight",
                                "NumRabbits",
                                "BirThresh",
                                "GrassRate" };
        return initParams;
    }

    /**
     * Returns the name of the simulation that is used in some toolbars
     * @return Name of simulation
     */
    public String getName() {
        // TODO Auto-generated method stub
        // MODIFIED
        return "Petri and Grimault's Rabbits simulation";
    }

    /**
     * This returns the schedule of the simulation. Purpose unknown at this time.
     * @return Schedule of the simulation
     */
    public Schedule getSchedule() {
        // TODO Auto-generated method stub
        // MODIFIED
        return schedule;
    }

    /**
     * Called when "Setup" (more like reset) button is pressed
     * "Setup" button -> returns the simulation to the uninitialized state
     */
    public void setup() {
        // TODO Auto-generated method stub
        // MODIFIED
        rSpace = null;
        rabbitList = new ArrayList();
        schedule = new Schedule(1);

        if (displaySurf != null){
            displaySurf.dispose();
        }
        displaySurf = null;
        displaySurf = new DisplaySurface(this, "Rabbit eating grass model - Window 1");
        registerDisplaySurface("Rabbit eating grass model - Window 1", displaySurf);
    }
}
