package concurent.student.second;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Base {

    private static final int STARTER_PEASANT_NUMBER = 5;
    private static final int PEASANT_NUMBER_GOAL = 10;
    private static final int FOOTMAN_NUMBER_GOAL = 10;
// lock to ensure only one unit can be trained at one time
    private final ReentrantLock trainingLock = new ReentrantLock();

    private final String name;
    private final Resources resources = new Resources();
    private final List<Peasant> peasants = Collections.synchronizedList(new LinkedList<>());
    private final List<Footman> footmen = Collections.synchronizedList(new LinkedList<>());
    private final List<Building> buildings = Collections.synchronizedList(new LinkedList<>());
    private final List<Personnel> army = Collections.synchronizedList(new LinkedList<>());

    public Base(String name){
        this.name = name;
        for(int i = 0; i < STARTER_PEASANT_NUMBER; ++i){
            new Thread(() -> {
                Peasant p = createPeasant();
                if( peasants.size() < 4 ){
                    p.startMining();
                }
                if( peasants.size() == 4){
                    p.startCuttingWood();
                }
            }).start();
        }
    }

    public void startPreparation(){
		
        while(buildings.stream().filter(b -> b.getUnitType() == UnitType.FARM).count() < 3){
            Peasant peasant = getFreePeasant();
            if(peasant != null){
                peasant.tryBuilding(UnitType.FARM);
            }
            sleepForMsec(10);
        }

        while(peasants.size() < PEASANT_NUMBER_GOAL){
            Peasant p = createPeasant();
            if(p != null) {
                if (peasants.size() < 8) {
                    p.startMining();
                }
                if (peasants.size() == 8) {
                    p.startCuttingWood();
                }
            }
            sleepForMsec(10);
        }

        while(buildings.stream().filter(b -> b.getUnitType() == UnitType.LUMBERMILL).count() < 1){
            Peasant peasant = getFreePeasant();
            if(peasant != null){
                peasant.tryBuilding(UnitType.LUMBERMILL);
            }
            sleepForMsec(10);
        }

        while(buildings.stream().filter(b -> b.getUnitType() == UnitType.BLACKSMITH).count() < 1){
            Peasant peasant = getFreePeasant();
            if(peasant != null){
                peasant.tryBuilding(UnitType.BLACKSMITH);
            }
            sleepForMsec(10);
        }

        while(buildings.stream().filter(b -> b.getUnitType() == UnitType.BARRACKS).count() < 1){
            Peasant peasant = getFreePeasant();
            if(peasant != null){
                peasant.tryBuilding(UnitType.BARRACKS);
            }
            sleepForMsec(10);
        }

        while(footmen.size() < FOOTMAN_NUMBER_GOAL){
            Footman f = createFootman();
            sleepForMsec(10);
        }

        peasants.forEach(p -> p.stopHarvesting());
        System.out.println(this.name + " finished creating a base");

    }
    /**
     * Assemble the army - call the peasants and footmen to arms
     * @param latch
     */
    public void assembleArmy(CountDownLatch latch){
        this.footmen.forEach(f -> this.army.add(f));
        this.peasants.forEach(p -> this.army.add(p));
        System.out.println(this.name + " is ready for war");
		// the latch is used to keep track of both factions
        latch.countDown();
    }
   /**
     * Starts a war between the two bases.
     *
     * @param enemy Enemy base's personnel
     * @param warLatch Latch to make sure they attack at the same time
     */
    public void goToWar(List<Personnel> enemy, CountDownLatch warLatch){
		// This is necessary to ensure that both armies attack at the same time
        warLatch.countDown();
        try {
		// Waiting for the army to be ready for war
            warLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        List<Thread> soldiersThread = new LinkedList<>();
        this.army.forEach(p -> {
            Thread t = new Thread(() -> p.startWar(enemy));
            soldiersThread.add(t);
            t.start();
        });

        soldiersThread.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
		// If our army has no personnel, we failed
        if(army.isEmpty()){
            System.out.println(this.name + " has lost the fight");
        } else {
            System.out.println(this.name + " has won the fight");
        }
    }
   /**
     * Resolves the event when a personnel dies;
     * Remove it from the army and update the capacity.
     * @param p The fallen personnel
     */
    public void signalPersonnelDeath(Personnel p){
        resources.updateCapacity(-1 * p.getUnitType().foodCost);
        army.remove(p);
        if(p.getUnitType() == UnitType.PEASANT){
            peasants.remove(p);
        }
        if(p.getUnitType() == UnitType.FOOTMAN){
            footmen.remove(p);
        }
        System.out.println(this.name + " has lost a " + p.getUnitType().toString());

    }
    /**
     * Returns a peasants that is currently free.
     * Being free means that the peasant currently isn't harvesting or building.
     *
     * @return Peasant object, if found one, null if there isn't one
     */
    private Peasant getFreePeasant(){
        return peasants.stream().filter(p -> p.isFree()).findFirst().orElse(null);
    }
    /**
     * Creates a peasant.
     * A peasant could only be trained if there are sufficient
     * gold, wood and food for him to train.
     *
     * At one time only one Peasant can be trained.
     *
     * @return The newly created peasant if it could be trained, null otherwise
     */
    private Peasant createPeasant(){
        Peasant result;
        if(resources.canTrain(UnitType.PEASANT.goldCost, UnitType.PEASANT.woodCost, UnitType.PEASANT.foodCost)){
            try {
                trainingLock.lockInterruptibly();

                TimeUnit.MILLISECONDS.sleep(UnitType.PEASANT.buildTime);
                resources.removeCost(UnitType.PEASANT.goldCost, UnitType.PEASANT.woodCost);
                resources.updateCapacity(UnitType.PEASANT.foodCost);
                result = Peasant.createPeasant(this);
                peasants.add(result);
                System.out.println(this.name + " created a peasant");
                return result;

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                trainingLock.unlock();
            }
        }
        return null;
    }

    private Footman createFootman(){
        Footman result;
        if(resources.canTrain(UnitType.FOOTMAN.goldCost, UnitType.FOOTMAN.woodCost, UnitType.FOOTMAN.foodCost) &&
            buildings.stream().filter(b -> b.getUnitType() == UnitType.BARRACKS).count() > 0){
            try {
                trainingLock.lockInterruptibly();

                TimeUnit.MILLISECONDS.sleep(UnitType.FOOTMAN.buildTime);
                resources.removeCost(UnitType.FOOTMAN.goldCost, UnitType.FOOTMAN.woodCost);
                resources.updateCapacity(UnitType.FOOTMAN.foodCost);
                result = Footman.createFootman(this);
                footmen.add(result);
                System.out.println(this.name + " created a footman");
                return result;

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                trainingLock.unlock();
            }
        }
        return null;
    }

    public Resources getResources(){
        return this.resources;
    }

    public List<Building> getBuildings(){
        return this.buildings;
    }

    public List<Personnel> getArmy(){
        return this.army;
    }

    public String getName(){
        return this.name;
    }
    /**
     * Helper method to determine if a base has the required number of a certain building.
     *
     * @param unitType Type of the building
     * @param required Number of required amount
     * @return true, if required amount is reached (or surpassed), false otherwise
     */
    private boolean hasEnoughBuilding(UnitType unitType, int required){
        // TODO check in the buildings list if the type has reached the required amount
        return false;
    }

    private static void sleepForMsec(int sleepTime) {
        try {
            TimeUnit.MILLISECONDS.sleep(sleepTime);
        } catch (InterruptedException e) {
        }
    }

}
