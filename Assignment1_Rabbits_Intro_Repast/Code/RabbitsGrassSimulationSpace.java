import uchicago.src.sim.space.Object2DGrid;

/**
 * Class that implements the simulation space of the rabbits grass simulation.
 * @author 
 */

public class RabbitsGrassSimulationSpace {
    private Object2DGrid grassSpace;
    private Object2DGrid rabbitSpace;
    private int maxRate;

    // We initialize the grid with Doubles representing the grass. It could be any Object though.
    public RabbitsGrassSimulationSpace(int xSize, int ySize, int maxGrass) {
        grassSpace = new Object2DGrid(xSize, ySize);
        rabbitSpace = new Object2DGrid(xSize, ySize);

        maxRate = maxGrass;

        for (int i=0; i<xSize; i++){
            for (int j=0; j<ySize; j++){
                grassSpace.putObjectAt(i, j, new Integer(0));
            }
        }
    }

    public void plantGrass(int grass){
        // Randomly place money in moneySpace
        for(int i = 0; i < grass; i++){

            // Choose coordinates
            int x = (int)(Math.random()*(grassSpace.getSizeX()));
            int y = (int)(Math.random()*(grassSpace.getSizeY()));

            // Get the value of the object at those coordinates
            int currentRate = getGrassAt(x, y);
            // Replace the Integer object with another one with the new value if and only if the maximum value isn't reached yet (otherwise, do nothing)
            if (currentRate < maxRate) {
                grassSpace.putObjectAt(x, y, new Integer(currentRate + 1));
            }
        }
    }

    public int getGrassAt(int x, int y){
        int i;
        if(grassSpace.getObjectAt(x,y)!= null){
            i = ((Integer) grassSpace.getObjectAt(x,y)).intValue();
        } else{
            i = 0;
        }
        return i;
    }

    public Object2DGrid getCurrentGrassSpace(){
        return grassSpace;
    }

    public Object2DGrid getCurrentRabbitSpace(){
        return rabbitSpace;
    }

    public boolean isCellOccupied(int x, int y){
        boolean retVal = false;
        if (rabbitSpace.getObjectAt(x, y) != null) retVal = true;
        return retVal;
    }

    public boolean addRabbit(RabbitsGrassSimulationAgent rabbit){
        // We try to put a Rabbit at a random place, with a maximum number of try
        boolean retVal = false;
        int count = 0;
        int countLimit = 10 * rabbitSpace.getSizeX() * rabbitSpace.getSizeY();

        while ((retVal==false) && (count < countLimit)){
            int x = (int)(Math.random()*(rabbitSpace.getSizeX()));
            int y = (int)(Math.random()*(rabbitSpace.getSizeY()));

            if(isCellOccupied(x,y) == false){
                rabbitSpace.putObjectAt(x, y, rabbit);
                rabbit.setXY(x,y);
                rabbit.setRabbitSpace(this);
                retVal = true;
            }

            count++;
        }

        return retVal;
    }

    public void removeRabbitAt(int x, int y){
        rabbitSpace.putObjectAt(x, y, null);
    }

    public int eatGrassAt(int x, int y){
        // Rabbits are gluttons and eat everything
        int grass = getGrassAt(x, y);
        grassSpace.putObjectAt(x, y, new Integer(0));
        return grass;
    }

    public void moveRabbitAt(int x, int y, int newX, int newY){
        // If there is already a rabbit at the desired location, the movement is simply cancelled
        if(!isCellOccupied(newX, newY)){
            RabbitsGrassSimulationAgent rab = (RabbitsGrassSimulationAgent) rabbitSpace.getObjectAt(x, y);
            removeRabbitAt(x, y);
            rab.setXY(newX, newY);
            rabbitSpace.putObjectAt(newX, newY, rab);
        }
    }

    public int getTotalGrass(){
        int totalGrass=0;
        for (int i = 0; i < rabbitSpace.getSizeX(); i++) {
            for (int j = 0; j < rabbitSpace.getSizeY(); j++) {
                totalGrass+=getGrassAt(i,j);
            }
        }
        return totalGrass;
    }
}
