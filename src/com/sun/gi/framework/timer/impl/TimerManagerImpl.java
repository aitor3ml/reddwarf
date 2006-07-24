/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.framework.timer.impl;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.PriorityQueue;

import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.SimTimerListener;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.impl.GLOReferenceImpl;

/**
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TimerManagerImpl implements TimerManager {
    
    private static long nextID = 0;

    // package visibility for faster inner-class access
    final Method callbackMethod;

    class TimerRec implements Comparable<TimerRec> {

        long evtID;
        long triggerTime;
        long repeatTime;

        Simulation sim;
        ACCESS_TYPE accessType;

        long objID;

        public TimerRec(long tid, long delay, boolean repeating,
                Simulation sim, ACCESS_TYPE access, long objID)
        {
            evtID = tid;
            triggerTime = System.currentTimeMillis() + delay;
            if (repeating) {
                this.repeatTime = delay;
            } else {
                this.repeatTime = -1;
            }
            this.sim = sim;
            this.objID = objID;
            accessType = access;
        }

        public int compareTo(TimerRec other) {
            if (triggerTime < other.triggerTime) {
                return -1;
            } else if (triggerTime > other.triggerTime) {
                return 1;
            } else {
                return 0;
            }
        }

    }

    PriorityQueue<TimerRec> queue = new PriorityQueue<TimerRec>();

    public TimerManagerImpl(final long heartbeat) throws InstantiationException {
        try {
            callbackMethod = SimTimerListener.class.getMethod("timerEvent",
                    new Class[] { long.class });
        } catch (SecurityException e1) {
            e1.printStackTrace();
            throw new InstantiationException();
        } catch (NoSuchMethodException e1) {
            e1.printStackTrace();
            throw new InstantiationException();
        }

        new Thread(new Runnable() {

            public void run() {
                while (true) {
                    long time = System.currentTimeMillis();
                    synchronized (queue) {
                        for (Iterator<TimerRec> i = queue.iterator(); i.hasNext();) {
                            TimerRec rec = i.next();
                            if (rec.triggerTime <= time) { // do event
                                rec.sim.queueTask(rec.sim.newTask(
                                        rec.accessType,
                                        new GLOReferenceImpl<SimTimerListener>(rec.objID),
                                        callbackMethod,
                                        new Object[] { rec.evtID }, null));
                                if (rec.repeatTime > 0) {
                                    rec.triggerTime = time + rec.repeatTime;
                                } else {
                                    i.remove();
                                }
                            } else { // out of events
                                break;
                            }
                        }
                    }
                    while (time + heartbeat > System.currentTimeMillis()) {
                        try {
                            Thread.sleep((time + heartbeat)
                                    - System.currentTimeMillis());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }).start();
    }

    public long registerEvent(long id, Simulation sim, ACCESS_TYPE access,
            long startObjectID, long delay, boolean repeat) {
        TimerRec rec = new TimerRec(id, delay, repeat, sim, access,
                startObjectID);
        synchronized (queue) {
            queue.add(rec);
        }
        return rec.evtID;
    }

    public void removeEvent(long eventID) {
        synchronized (queue) {
            for (Iterator<TimerRec> i = queue.iterator(); i.hasNext();) {
                TimerRec rec = i.next();
                if (rec.evtID == eventID) {
                    i.remove();
                }
            }
        }

    }

    public synchronized long getNextTimerID() {
        return nextID++;
    }
}