package org.codehaus.mojo.xml.autodetect;

import javax.xml.stream.events.XMLEvent;

/**
 * Detector for XML Event.<br>
 * If detector interested in event it will process the action.
 */
public interface XMLEventDetector {
	/**
	 * Is this detector interested in current event
	 * 
	 * @param event
	 *            Current XMLEvent to process
	 * @return true, if this XMLEvent should be processed
	 */
	public boolean interested(XMLEvent event);

	/**
	 * Process current XMLEvent
	 * 
	 * @param event
	 *            current XML event
	 */
	public void process(XMLEvent event);

	/**
	 * Need to delete this Detector from queue
	 * 
	 * @return true if need to delete
	 */
	public boolean finished();
}
