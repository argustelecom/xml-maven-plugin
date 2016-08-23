package org.codehaus.mojo.xml.autodetect;

import java.util.Iterator;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.mojo.xml.validation.ValidationSet;

/**
 * Detector for retriving systemId from XSD schema
 */
public class XSDdetector implements XMLEventDetector {
	private final ValidationSet params;
	private boolean finished = false;

	/**
	 * Initialize XSDdetector with ValidationSet
	 * @param params ValidationSet, in which systemId will be replaced
	 */
	public XSDdetector(ValidationSet params) {
		this.params = params;
	}

	@Override
	public boolean interested(XMLEvent event) {
		return (event.getEventType() == XMLStreamConstants.START_ELEMENT);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void process(XMLEvent event) {
		Iterator<Attribute> attribs = event.asStartElement().getAttributes();
		Attribute attr = null;
		while (attribs.hasNext()) {
			attr = attribs.next();
			if (attr.getValue().contains(".xsd")) {
				// Skip namespace part in schema path
				int pos = 0;
				if (attr.getValue() != null)
					pos = attr.getValue().lastIndexOf(" ");
				if (pos > 0) {
					params.setSystemId(attr.getValue().substring(pos + 1));
				} else {
					params.setSystemId(attr.getValue());
				}
				break;
			}
		}
		finished = true;
	}

	@Override
	public boolean finished() {
		return finished;
	}

}
