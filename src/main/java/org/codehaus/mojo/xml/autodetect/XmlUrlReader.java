package org.codehaus.mojo.xml.autodetect;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.maven.plugin.logging.Log;

/**
 * Read XML data from XML URL by delegating work to added detectors.
 */
public class XmlUrlReader {
	@SuppressWarnings("unused")
	private Log logger = null;
	
	private final URL sourceUrl;
	private ConcurrentLinkedQueue<XMLEventDetector> detectors = new ConcurrentLinkedQueue<XMLEventDetector>();

	public XmlUrlReader(URL readerURL) {
		this.sourceUrl = readerURL;
	}

	/**
	 * Create reader with maven logger
	 * @param readerURL URL for reading
	 * @param logger maven log
	 */
	public XmlUrlReader(URL readerURL, Log logger) {
		this(readerURL);
		this.logger = logger;
	}

	/**
	 * Add XML Event Detector to Reader
	 * @param detector Detector for perfoming action
	 * @return Current XmlUrlReader instance
	 */
	public XmlUrlReader addDetector(XMLEventDetector detector) {
		detectors.add(detector);
		return this;
	}

	/**
	 * Multiple adding of detectors.
	 * @param list List of XML Event Detectors
	 */
	public void addDetectors(List<XMLEventDetector> list) {
		for (XMLEventDetector detector : list) {
			this.detectors.add(detector);
		}
	}

	/**
	 * Read document with initialized Detectors.
	 */
	public void read() {
		XMLInputFactory inputFactory = XMLInputFactory.newInstance();
		InputStream in = null;
		try {
			in = this.sourceUrl.openStream();
		} catch (IOException e) {
			throw new IllegalStateException("Can't open stream for " + this.sourceUrl.getPath());
		}
		XMLEventReader eventReader = null;
		try {
			eventReader = inputFactory.createXMLEventReader(in);
		} catch (XMLStreamException e) {
			throw new IllegalStateException("Can't prepare xml reader for " + this.sourceUrl.getPath());
		}
		while (eventReader.hasNext()) {
			XMLEvent event = (XMLEvent) eventReader.next();

			if (detectors.isEmpty())
				break;
			Iterator<XMLEventDetector> it = detectors.iterator();
			XMLEventDetector detector = null;
			while (it.hasNext()) {
				detector = it.next();
				if (detector.interested(event)) {
					detector.process(event);
				}
				if (detector.finished()) {
					this.detectors.remove(detector);
				}
			}

		}
		try {
			in.close();
		} catch (IOException e) {
			throw new IllegalStateException("Can't close resource for " + this.sourceUrl.getPath());
		}
	}

	/**
	 * Interface for handlers, who can perform some actions on xmlevent
	 */
	public interface ReadProcessHandler {
		public boolean interested(XMLEvent event);

		public void process(XMLEvent event);
	}

}
