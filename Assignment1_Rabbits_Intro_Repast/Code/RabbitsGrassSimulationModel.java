import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimModelImpl;

// Import due to tutorial
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.gui.Value2DDisplay;
import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.space.Object2DGrid;
import uchicago.src.sim.util.SimUtilities;
import uchicago.src.sim.analysis.DataSource;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.analysis.Sequence;

import java.awt.Color;
import java.awt.geom.Arc2D;
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
    /* Schedule object */
    private Schedule schedule;

    /* The Space object where we run the model */
    private RabbitsGrassSimulationSpace rSpace;

    /* Object needed for display */
    private DisplaySurface displaySurf;

    /* List of Rabbits agents */
    private ArrayList rabbitList;

    /* Graph of amount of grass in space */
    private OpenSequenceGraph amountOfGrassInSpace;

    /* Default values for the parameters */
    private static final int GRIDWIDTH = 20;
    private static final int GRIDHEIGHT = 20;
    private static final int NUMRABBITS = 42;
    private static final int BIRTHRESH = 42;
    private static final int GRASSRATE = 42;
    private static final int EXHAUSTRATE = 2;
    private static final int MAXSHADES = 50;

    /* Parameters of the simulation */
    public int gridWidth = GRIDWIDTH;
    public int gridHeight = GRIDHEIGHT;
    public int numRabbits = NUMRABBITS;
    public int birThresh = BIRTHRESH;
    public int grassRate = GRASSRATE;
    public int exhaustRate = EXHAUSTRATE;
    public int maxShades = MAXSHADES;

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

    public int getMaxShades(){
        return maxShades;
    }

    public void setMaxShades(int maxShades){
        this.maxShades = maxShades;
    }

    public int getExhaustRate(){
        return exhaustRate;
    }

    public void setExhaustRate(int exhaustRate){
        this.exhaustRate = exhaustRate;
    }

    /**
     * This returns a list of all parameters that can be modified when running the simulation.
     * The parameters must be named like exampleParam, have get and set methods named getExampleParam and setExampleParam
     *  respectively, and the corresponding string in initParams must be named "ExampleParam"
     * @return List of parameters names
     */
    public String[] getInitParam() {
        String[] initParams = { "GridWidth",
                "GridHeight",
                "NumRabbits",
                "BirThresh",
                "GrassRate",
                "ExhaustRate",
                "MaxShades" };
        return initParams;
    }

    public static void main(String[] args) {

        System.out.println("Rabbit skeleton");

        SimInit init = new SimInit();
        RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
        init.loadModel(model, "", false);

    }

    /**
     * grassInSpace implements Datasource and Sequence,
     * It requires 2 methods, execute and getSValue which aims at returning
     * the total amount of grass in the space
     */
    class grassInSpace implements DataSource,Sequence{
        public Object execute(){
            return new Double(getSValue());
        }

        public double getSValue(){
            return (double)rSpace.getTotalGrass();
        }
    }

    class rabbitsInSpace implements DataSource,Sequence{
        public Object execute(){
            return new Double(getSValue());
        }

        public double getSValue(){
            return (double)rabbitList.size()*10.;
        }
    }
    /**
     * Called when "Initialize" button is clicked
     * "Initialize" button -> simulation supposed uninitialized and initialize it
     * Commonly divided into 3 sub-methods
     */
    public void begin() {
        buildModel();
        buildSchedule();
        buildDisplay();

        displaySurf.display();
        amountOfGrassInSpace.display();
    }

    public void buildModel() {
        rSpace = new RabbitsGrassSimulationSpace(gridWidth, gridHeight, maxShades);
        rSpace.plantGrass(grassRate);

        for (int i = 0; i < numRabbits; i++){
            addNewRabbit();
        }
    }

    public void buildSchedule() {
        class RabbitStep extends BasicAction {
            public void execute(){
                SimUtilities.shuffle(rabbitList);
                for(int i = 0; i < rabbitList.size(); i++){
                    RabbitsGrassSimulationAgent rab = (RabbitsGrassSimulationAgent) rabbitList.get(i);

                    if (!rab.getNewlyBorn()) {
                        // Internal evolution of the rabbit and movement
                        rab.step();

                        // Rabbit reproduce
                        if (rab.getEnergy() >= birThresh){
                            addNewRabbit();
                            rab.setEnergy(rab.getEnergy() - birThresh*9/10);
                        }

                        // Rabbit DEAD
                        if (rab.getEnergy() < 1){
                            rSpace.removeRabbitAt(rab.getX(), rab.getY());
                            rabbitList.remove(i);
                        }
                    } else {
                        rab.setNewlyBorn(false);
                    }
                }

                rSpace.plantGrass(grassRate);
                displaySurf.updateDisplay();
            }
        }

        schedule.scheduleActionBeginning(0, new RabbitStep());

        class RabbitsUpdateGrassInSpace extends BasicAction{
            public void execute(){
                amountOfGrassInSpace.step();
            }
        }

        schedule.scheduleActionAtInterval(10,new RabbitsUpdateGrassInSpace());
    }

    public void buildDisplay() {
        ColorMap map = new ColorMap();

        map.mapColor(0, new Color(102, 51, 0));

        // Depending on the quantity of grass in the cell, the color linearly goes on a scale from brown (102,51,0) to green (0,127,0)
        // However, if the level of grass goes too high, this causes a malfunction and black cells appear at the top-left corner of the grid
        // This also causes little columns of cells to act as one (same level of grass, and everything is eaten is one is).
        // We didn't take the time to look into it and simply increased the maximum number of shades.
        for(int i = 1; i<=maxShades; i++){
            map.mapColor(i,
                    new Color((int) (102. * ((float)(maxShades-i))/((float)(maxShades))),
                            (int) (51. * (float)(((float)(maxShades-i))/((float)(maxShades))) + (float)(((float)i)/((float)(maxShades))*127.)),
                            0));
        }

        Value2DDisplay displayGrass = new Value2DDisplay(rSpace.getCurrentGrassSpace(), map);

        Object2DDisplay displayRabbits = new Object2DDisplay(rSpace.getCurrentRabbitSpace());
        displayRabbits.setObjectList(rabbitList);

        displaySurf.addDisplayable(displayGrass, "Grass");
        displaySurf.addDisplayable(displayRabbits, "Agents");

        amountOfGrassInSpace.addSequence("Grass In Space",new grassInSpace());
        amountOfGrassInSpace.addSequence("Number of Rabbits times 10",new rabbitsInSpace());
    }

    /**
     * Add 1 rabbit to the list of rabbits. Does NOT place it into the space.
     */
    private void addNewRabbit(){
        RabbitsGrassSimulationAgent r = new RabbitsGrassSimulationAgent(exhaustRate);
        if(rSpace.addRabbit(r)) {
            rabbitList.add(r);
        }
    }

    /**
     * Returns the name of the simulation that is used in some toolbars
     * @return Name of simulation
     */
    public String getName() {
        return "Petri and Grimault's Rabbits simulation";
    }

    /**
     * This returns the schedule of the simulation. Purpose unknown at this time.
     * @return Schedule of the simulation
     */
    public Schedule getSchedule() {
        return schedule;
    }

    /**
     * Called when "Setup" (more like reset) button is pressed
     * "Setup" button -> returns the simulation to the uninitialized state
     */
    public void setup() {
        rSpace = null;
        rabbitList = new ArrayList();
        schedule = new Schedule(1);
        //Dispose of the existing displays
        if (displaySurf != null){
            displaySurf.dispose();
        }
        displaySurf = null;

        if (amountOfGrassInSpace!=null){
            amountOfGrassInSpace.dispose();
        }
        amountOfGrassInSpace = null;

        //Create Displays
        displaySurf = new DisplaySurface(this, "Rabbit eating grass model - Window 1");
        amountOfGrassInSpace = new OpenSequenceGraph("Plot",this);

        //Register Displays
        registerDisplaySurface("Rabbit eating grass model - Window 1", displaySurf);
        this.registerMediaProducer("Plot",amountOfGrassInSpace);
    }
}
