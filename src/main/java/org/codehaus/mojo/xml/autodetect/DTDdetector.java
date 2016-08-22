package org.codehaus.mojo.xml.autodetect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.mojo.xml.validation.ValidationSet;

/**
 * Detector for retriving systemId and publicId from DTD schema
 */
public class DTDdetector implements XMLEventDetector {
	private final ValidationSet params;
	private boolean finished = false;

	/**
	 * Initialize DTDdetector with ValidationSet
	 * @param params ValidationSet, in which systemId and publicId will be replaced
	 */
	public DTDdetector(ValidationSet params) {
		this.params = params;
	}

	@Override
	public boolean interested(XMLEvent event) {
		return (event.getEventType() == XMLStreamConstants.DTD);
	}

	@Override
	public void process(XMLEvent event) {
		DTD dtd = (DTD) event;
		String DTDdata = dtd.getDocumentTypeDeclaration();
		Pattern p = null;
		Matcher m = null;

		p = Pattern.compile("(\".*.dtd\")");
		m = p.matcher(DTDdata);
		if (m.find()) {
			this.params.setSystemId(m.group().replace("\"", ""));
		}

		p = Pattern.compile("(\"(\\+|-).*?\")");
		m = p.matcher(DTDdata);
		if (m.find()) {
			this.params.setPublicId(m.group().replace("\"", ""));
		}
		finished = true;
	}

	@Override
	public boolean finished() {
		return finished;
	}

}
