package org.codehaus.mojo.xml.autodetect;

import java.util.Iterator;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

/**
 * Detector for retriving NameSpace from document
 */
public class NameSpaceDetector implements XMLEventDetector {
	private String nameSpace = "";
	private boolean finished = false;

	/**
	 * Get readed NameSpace attribute value
	 * @return 
	 */
	public String getNameSpace() {
		return nameSpace;
	}

	@Override
	public boolean interested(XMLEvent event) {
		return (event.getEventType() == XMLStreamConstants.START_ELEMENT);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void process(XMLEvent event) {
		Attribute attr = null;
		Iterator<Attribute> attributes = event.asStartElement().getNamespaces();
		while (attributes.hasNext()) {
			attr = attributes.next();
			if (!attr.getName().getLocalPart().equals("xsi")) {
				this.nameSpace = attr.getValue();
			}
		}
		//Return targetNameSpace
		attributes = event.asStartElement().getAttributes();
		while (attributes.hasNext()) {
			attr = attributes.next();
			if ( attr.getName().getLocalPart().equals("targetNamespace") ) {
				this.nameSpace = attr.getValue();
			}
		}
		
		finished = true;
	}

	@Override
	public boolean finished() {
		return finished;
	}

}
