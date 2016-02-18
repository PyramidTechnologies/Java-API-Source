/*
 * Copyright (C) 2014 Pyramid Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.pyramidacceptors.ptalk.api;

import static com.pyramidacceptors.ptalk.api.event.Events.*;
import com.pyramidacceptors.ptalk.api.event.*;
import java.util.EnumSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Pyramid Acceptor class is the realization of an {@code ICommDevice}.<br>
 * It uses a RS-232 socket by default.<br>
 * <br>
 * @author <a href="mailto:cory@pyramidacceptors.com">Cory Todd</a>
 * @since 1.0.0.0
 */
public class PyramidAcceptor implements ICommDevice, PTalkEventListener {
    private final Logger logger = LoggerFactory.getLogger(PyramidAcceptor.class);

    private Courier courier;
    private final PyramidPort port;    

    // Use CopyOnWriteArrayList to avoid ConcurrentModificationExceptions if a
    // listener attempts to remove itself during event notification.
    private final CopyOnWriteArrayList<PTalkEventListener> listeners 
            = new CopyOnWriteArrayList<>();
    
    
    /**
     * Create a new PyramidAcceptor
     * 
     * @param port string port name
     */
    private PyramidAcceptor(PyramidPort port) {
        this.port = port; 
    }

    /**
     * Used only for testing
     */
    private PyramidAcceptor() {
        this.port = null;
    }
    
    /**
     * Instantiate a new Pyramid Acceptor with an existing port<br>
     * <br>
     * @param portName OS string name of the port this is connected to
     * @return New instance of PyramidAcceptor
     * @throws com.pyramidacceptors.ptalk.api.PyramidDeviceException thrown if underlying port
     * cannot be opened.
     */
    public static PyramidAcceptor valueOfRS232(String portName) throws PyramidDeviceException {
        return new PyramidAcceptor(new PyramidPort.PortBuilder(portName).build());
    }
    
    /**
     * Generate a new PyramidAcceptor with a custom port configuration. This
     * instance of PyramidPort will still use the standard RS-232 packet unless
     * otherwise specified.
     * 
     * @param config Configuration to use
     * @param portName OS name of port
     * @param baudRate integer baud rate
     * @param databits number of bits per unit of data
     * @param stopbits bits to indicate end of data
     * @param parity type of parity
     * @return new instance of PyramidAcceptor
     * @throws com.pyramidacceptors.ptalk.api.PyramidDeviceException thrown if underlying port
     * cannot be opened.
     */
    public static PyramidAcceptor valueOfRS232(RS232Configuration config, String portName, int baudRate,
            int databits, int stopbits, int parity) throws PyramidDeviceException {
            return new PyramidAcceptor(new PyramidPort.PortBuilder(portName)
                    .baudRate(baudRate).dataBits(databits).stopBits(stopbits)
                    .parity(parity).build());

    }
    
    /**
     * Attempt to autodetect the connected slave and use a default RS-232<br>
     * configuration.
     * @return a new instance of PyramidAcceptor
     * @throws com.pyramidacceptors.ptalk.api.PyramidDeviceException thrown if underlying port
     * cannot be opened.
     */
    public static PyramidAcceptor valueOfRS232() throws PyramidDeviceException {
        String portName = PortScanner.find();
        if(!portName.equals("")){
            return new PyramidAcceptor(new PyramidPort.PortBuilder(portName).build());
        } else {
            throw new PyramidDeviceException("Unable to autodetect device", 
                    "", "");
        }
    }

    /**
     * Returns an object that does not touch any serial ports.
     * @return PyramidAcceptor dummy instance (no physical port connection)
     */
    public static PyramidAcceptor asTest() {
        return new PyramidAcceptor();
    }


    /**
     * Returns the firmware revision of the target acceptor in the format
     * major.minor. e.g. 1.13. This may return an empty string if zero slave
     * messages have been received. This may occur during first power up. Also
     * note that this field is reported on every message so it is normal to check
     * back periodically until you have a valid firmware revision string
     * @return string
     */
    public String getFirmwareRevision() {
        return courier.getFirmwareRevision();
    }

    /**
     * Returns the model of the target acceptor in the format This may AcceptorModel.Unknown
     * until the 1st valid slave message is received. Also note that this field is reported on
     * every message so it is normal to check back periodically until you have a valid acceptor model.
     * @return string
     */
    public AcceptorModel getAcceptorModel() {
        return courier.getAcceptorModel();
    }

    /**
     * Request the slave to perform a power cycle
     * @since 1.2.4
     */
    public void requestReset(){
        courier.requestReset();
    }

     /**
     * Subscribe to events generated by this instance<br>. To apply an<br>
     * event filter, please set the eventMask in the relevant IConfiguration<br>
     * implemenmation.
     * @see com.pyramidacceptors.ptalk.api.RS232Configuration
     * @see com.pyramidacceptors.ptalk.api.RS232Configuration
     * <br>
     * @param l PTalkEventListener subscriber object
     */
    public void addChangeListener(PTalkEventListener l) {
      this.listeners.add(l);
    }

    /**
     * Unsubscribe to events generated by this instance<br>. To apply an <br>
     * event filter, please set the eventMask in the relevant IConfiguration<br>
     * implemenmation.
     * @see com.pyramidacceptors.ptalk.api.RS232Configuration
     * @see com.pyramidacceptors.ptalk.api.RS232Configuration
    * <br>
    * @param l PTalkEventListener
    */    
    public void removeChangeListener(PTalkEventListener l) {
      this.listeners.remove(l);
    }   
    
    private void fireChangeEvent(PTalkEvent e) {
        for(PTalkEventListener l : listeners) {
            l.changeEventReceived(e);
        }
    }
    
    @Override
    public void connect() {             
        try {
            
            // If port is already open or can be opened successfully,
            // create a new courier and start polling
            if(port.isOpen() || port.openPort()) {
                
                courier = new Courier(port, new RS232Socket());
                courier.addChangeListener(this);
                courier.start();
                logger.info("Connected to device on port {}",
                        port.getPortName());
            
            } else {

                logger.error("Failed to connect device on port {}",
                        port.getPortName());
            }
            
        }catch(PyramidDeviceException ex) {
            logger.error("Failed to connect: {}", ex);
        }
    }

    @Override
    public void disconnect() {      
       
        try {
            // Stop the message courier
            if(courier != null) {
                courier.stopThread();
                courier.removeChangeListener(this);
            }
            
            // And then see if we can close the port...
            if(port.closePort()) {
                logger.info("Disconnect device from port {}",
                        port.getPortName());
            
            } else {
                logger.error("Failed to disconnect device on port {}",
                        port.getPortName());
            }
            
        } catch(PyramidDeviceException ex) {
            logger.error("Failed to disconnect: {}", ex);
        }
    }

    @Override
    public int getPollRate() {
        return RS232Configuration.INSTANCE.getPollrate();
    }
    
    @Override
    public boolean setPollRate(int rate) {
        return RS232Configuration.INSTANCE.setPollrate(rate);
    }

    @Override
    public boolean isConnected() {
        return port.isOpen();
    }

    @Override
    public String getPortName() {
        return port.getPortName();
    }

    @Override
    public void changeEventReceived(PTalkEvent evt) {
        
        // TODO implement event filter
        
        EnumSet<Events> event = evt.getEventId();
        int eventMask = RS232Configuration.INSTANCE.getEventMask();
        
        // States - only one state allowed at a time so save
        if(event.contains(Idling) && 
                ((Idling.getIntId()& eventMask) == Idling.getIntId())) {
            fireChangeEvent(new IdlingEvent(evt));
        }
        if(event.contains(Accepting) && 
                ((Accepting.getIntId()& eventMask) == Accepting.getIntId())) {
            fireChangeEvent(new AcceptingEvent(evt));
        }
        if(event.contains(Escrowed) && 
                ((Escrowed.getIntId()& eventMask) == Escrowed.getIntId())) {
            fireChangeEvent(new EscrowedEvent(evt));
        }
        if(event.contains(Stacking) && 
                ((Stacking.getIntId()& eventMask) == Stacking.getIntId())) {
            fireChangeEvent(new StackingEvent(evt));
        }        
        if(event.contains(Returning) && 
                ((Returning.getIntId()& eventMask) == Returning.getIntId())) {
            fireChangeEvent(new ReturningEvent(evt));
        }                
        if(event.contains(Stacked) && 
                ((Stacked.getIntId()& eventMask) == Stacked.getIntId())) {
            fireChangeEvent(new StackedEvent(evt));
        }
        if(event.contains(BillJammed) && 
                ((BillJammed.getIntId()& eventMask) == BillJammed.getIntId())) {
            fireChangeEvent(new BillJammedEvent(evt));
        }        
        if(event.contains(StackerFull) && 
                ((StackerFull.getIntId()& eventMask) == StackerFull.getIntId())) {
            fireChangeEvent(new StackerFullEvent(evt));
        } 
        
        // Events - there can be multiple so check them all
        if(event.contains(Returned) && 
                ((Returned.getIntId()& eventMask) == Returned.getIntId())) {
            fireChangeEvent(new ReturnedEvent(evt));
        }
        if(event.contains(Cheated) && 
                ((Cheated.getIntId()& eventMask) == Cheated.getIntId())) {
            fireChangeEvent(new CheatedEvent(evt));
        }
        if(event.contains(BillRejected) && 
                ((BillRejected.getIntId()& eventMask) == BillRejected.getIntId())) {
            fireChangeEvent(new BillRejectedEvent(evt));
        } 
        if(event.contains(BillCasetteRemoved) && 
                ((BillCasetteRemoved.getIntId()& eventMask) == BillCasetteRemoved.getIntId())) {
            fireChangeEvent(new CasseteMissingEvent(evt));
        }  
        if(event.contains(Failure) && 
                ((Failure.getIntId()& eventMask) == Failure.getIntId())) {
            fireChangeEvent(new FailureEvent(evt));
        }  
        if(event.contains(InvalidCommand) && 
                ((InvalidCommand.getIntId()& eventMask) == InvalidCommand.getIntId())) {
            fireChangeEvent(new InvalidMessageEvent(evt));
        }          
        if(event.contains(PowerUp) && 
                ((PowerUp.getIntId()& eventMask) == PowerUp.getIntId())) {
            fireChangeEvent(new PowerUpEvent(evt));
        }          
        if(event.contains(Credit) && 
                ((Credit.getIntId()& eventMask) == Credit.getIntId())) {
            fireChangeEvent(new CreditEvent(evt));
        }          

        // If we caught a generic event or something that we couldn't parse
        if(event.contains(Generic) || event.isEmpty() && ((Generic.getIntId()& eventMask) == Generic.getIntId())) {
                fireChangeEvent(evt);                
        }
    }
}
