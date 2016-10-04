import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;
import uchicago.src.sim.space.Object2DGrid;

import java.awt.Color;


/**
 * Class that implements the simulation agent for the rabbits grass simulation.

 * @author
 */

public class RabbitsGrassSimulationAgent implements Drawable {
	/* Parameters of the Rabbits */
	private int x;
	private int y;
	private int energy;
//	private static int IDNumber = 0;
//	private int ID;
	private RabbitsGrassSimulationSpace rSpace;
	private int exhaust;
	private boolean newlyBorn;

	public RabbitsGrassSimulationAgent(int exhaustRate){
		x = -1;
		y = -1;
		energy = 42;
//		IDNumber++;
//		ID = IDNumber;
		exhaust = exhaustRate;
		newlyBorn = true;
	}

	public void draw(SimGraphics arg0) {
		arg0.drawFastRoundRect(Color.lightGray);
	}

	public void setXY(int newX, int newY){
		setX(newX);
		setY(newY);
	}

	public int getX() {
		return x;
	}

	public void setX(int newX){
		x = newX;
	}

	public int getY() {
		return y;
	}

	public void setY(int newY){
		y = newY;
	}

	public int getEnergy(){
		return energy;
	}

	public void setEnergy(int newEnergy){
		energy = newEnergy;
	}

	public boolean getNewlyBorn(){
		return newlyBorn;
	}

	public void setNewlyBorn(boolean newNewlyBorn){
		newlyBorn = newNewlyBorn;
	}

	public void setRabbitSpace(RabbitsGrassSimulationSpace rs){
		rSpace = rs;
	}

	public void step(){
		// Decide new direction
		int dirX = 0;
		int dirY = 0;
		int dir = (int) (Math.random() * 4);

		switch(dir){
			case 0: //N
				dirY = -1;
				break;
			case 1: //S
				dirY = 1;
				break;
			case 2: //E
				dirX = 1;
				break;
			case 3: //W
				dirX = -1;
				break;
		}

		Object2DGrid grid = rSpace.getCurrentRabbitSpace();
		int newX = (x + dirX + grid.getSizeX()) % grid.getSizeX();
		int newY = (y + dirY + grid.getSizeY()) % grid.getSizeY();
		rSpace.moveRabbitAt(x, y, newX, newY);

		energy += rSpace.eatGrassAt(x, y);
		energy -= exhaust;
	}
}
