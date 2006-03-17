/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/


package com.sun.gi.apps.swordworld;

import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;

/**
 *
 * <p>Title: Player.java</p>
 * <p>Description: </p>
 * <p>This class is the Game Logic Class that defines Player-GLOs
 * in the SwordWorld demo.</p>  
 * <p>Player-GLOs are a very important concept in the SGS.  They
 * act like the user's "body" in the world of the GLOs.  They receieve
 * commands from the client program and based on those commands they
 * act on and change other GLOs.</p>
 * <p>In order to be a Player-GLO, a GLO's defining  GLC
 * must implement the SimUserDataListener event and the GLO must
 * be registered as the SimUserDataListener for that user's session.
 * <p>See SwordWorldBoot.userJoined() to see how registration of
 * a SimUserDataListener is accomplished.</p>
 * @see SwordWorldBoot.userJoined() 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class Player implements SimUserDataListener {
	// The name of the player, used by Room to
	// help create the room description.
	String name;
	// A GLOReference to our current Room.  This is
	// so we can be removed from the room when we leave.
	GLOReference<Room> currentRoomRef;
	// In the current version of the API, the only way for
	// the server to send data back down to the client is through
	// a channel.  (this is likely to chnage in future versions)
	// For now, both the client and the SwordWorldBoot open the
	// channel "GAMECHANNEL".  This is a field to store the
	// ChannelID that SwordWorldBoot was given for that channel.
	private ChannelID appChannel;
	/**
	 * @param string
	 */
	public Player(String string,ChannelID cid) {
		name = string;
		appChannel = cid;
		// TODO Auto-generated constructor stub
	}

	// This is called to set what room we think we are 
	// currently in.
	public void setCurrentRoom(GLOReference<Room> room){
		currentRoomRef = room;
	}
	/**
	 * All GLOs should define a dumym serialVersionUID.  This
	 * turns off version checking so we can change the class
	 * and still load old data that might already be in the ObjectStore.
	 */
	private static final long serialVersionUID = 1L;
	/* *
	 * This is the callback that is called when data arrives at the
	 * server from the user we registered ourselves as the 
	 * SimUserDataListener for.
	 * 
	 * @see com.sun.gi.logic.SimUserDataListener#userDataReceived(com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void userDataReceived(UserID from, ByteBuffer data) {
		// in this app, all the data are string commands
		// so we read all the bytes from the buffer and turn
		// them back into a string
		byte[] inbytes = new byte[data.remaining()];
		data.get(inbytes);
		String commandString = new String(inbytes).trim();
		// this is just a debug printf to show whatcommand we recieved
		System.out.println("CommandString ="+commandString);
		// we split the string up into indvidual words
		String[] words = StringUtils.explode(commandString," ");
		// we are going to need a simTask, so we get the current
		// one here.
		SimTask simTask = SimTask.getCurrent();
		// word[0] is the basic command.  The only command
		// we know so far is "look"
		if (words[0].equalsIgnoreCase("look")){
			// we get the actual Room GLO for the
			// room we are currently in.
			Room roomGLO = currentRoomRef.get(simTask);
			try {
				// the getDescription command requires we pass in a
				// GLOReference to us so it knows who not to put in
				// the description.
				// Sicne we dont have a GLOReference to ourselves
				// handy, we ask the SimTask to make us
				// one
				GLOReference<Player> myRef = simTask.lookupReferenceFor(this);
				// now we ask the Room GLO for the description.
				String out = roomGLO.getDescription(myRef);
				// We take the string description and write
				// its byets to a ByteBuffer so we can send it back
				// to the client.
				ByteBuffer outbuff = ByteBuffer.allocate(out.length());
				outbuff.put(out.getBytes());
				// we ask the SimTask to send the data to the client
				// who we received the command from.
				// We send it on the "GAMECHANNEL" channel that
				// SwordWorldBoot opened and told us about.
				// We want gauranteed delivery of the message,
				// so we set the reliable delivery flag to true.
				simTask.sendData(appChannel,from,outbuff,true);
			} catch (InstantiationException e) {
				// We *should* never see this exception.
				// If we did then somethign went very wrong,
				// like perhapse we asked the SimTask to make
				// a reference to a GLO that this task had not
				// yet acquired.
				System.out.println("Failed to create this-reference");
				e.printStackTrace();
			}
		}
		
	}

	/* *
	 * A callabck telling us when the user who we are registered as
	 * the SimUserDataListener for joins a channel.  We dont need
	 * that information in this app so we ignore it.
	 * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * A callabck telling us when the user who we are registered as
	 * the SimUserDataListener for leaves a channel.  We dont need
	 * that information in this app so we ignore it.
	 * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userLeftChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * The server can "snoop" channels by using the SimTask to tell the
	 * SGS to call this method every time data is sent by a particualr user on
	 * a particualr channel.
	 * We dont need this functionality so we leave this callback empty.
	 * @see com.sun.gi.logic.SimUserDataListener#dataArrivedFromChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void dataArrivedFromChannel(ChannelID cid, UserID from, ByteBuffer buff) {
		
	}

	/**
	 * This method returns our player name. Its used when others
	 * ask the Room GLO to describe the room.
	 * @return our player name
	 */
	public String getName() {

		return name;
	}

}
