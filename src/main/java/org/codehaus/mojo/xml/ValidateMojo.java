package org.codehaus.mojo.xml;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.NoSuchElementException;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.mojo.xml.autodetect.DTDdetector;
import org.codehaus.mojo.xml.autodetect.NameSpaceDetector;
import org.codehaus.mojo.xml.autodetect.XSDdetector;
import org.codehaus.mojo.xml.autodetect.XmlUrlReader;
import org.codehaus.mojo.xml.validation.ValidationSet;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * The ValidatorMojo's task is the validation of XML files against a given schema.
 */
@Mojo( name = "validate", defaultPhase = LifecyclePhase.TEST, threadSafe = true )
public class ValidateMojo
    extends AbstractXmlMojo
{
    /**
     * Specifies a set of document types, which are being validated.
     */
    @Parameter
    private ValidationSet[] validationSets;

    /**
     * Reads a validation sets schema.
     * 
     * @param pResolver The resolver to use for loading external entities.
     * @param pValidationSet The validation set to configure.
     * @return The validation sets schema, if any, or null.
     * @throws MojoExecutionException Loading the schema failed.
     */
    private Schema getSchema( Resolver pResolver, ValidationSet pValidationSet )
        throws MojoExecutionException
    {
        final String publicId = pValidationSet.getPublicId();
        final String systemId = pValidationSet.getSystemId();
        if ( ( publicId == null || "".equals( publicId ) ) && ( systemId == null || "".equals( systemId ) ) )
        {
            return null;
        }

        getLog().debug( "Loading schema with public Id " + publicId + ", system Id " + systemId );
        InputSource inputSource = null;
        if ( pResolver != null )
        {
            try
            {
                inputSource = pResolver.resolveEntity( publicId, systemId );
            }
            catch ( SAXException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
        }
        if ( inputSource == null )
        {
            inputSource = new InputSource();
            inputSource.setPublicId( publicId );
            inputSource.setSystemId( systemId );
        }
        final SAXSource saxSource = new SAXSource( inputSource );

        String schemaLanguage = pValidationSet.getSchemaLanguage();
        if ( schemaLanguage == null || "".equals( schemaLanguage ) )
        {
            schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
        }
        try
        {
            SchemaFactory schemaFactory = SchemaFactory.newInstance( schemaLanguage );
            if ( pResolver != null )
            {
                schemaFactory.setResourceResolver( pResolver );
            }
            return schemaFactory.newSchema( saxSource );
        }
        catch ( SAXException e )
        {
            throw new MojoExecutionException( "Failed to load schema with public ID " + publicId + ", system ID "
                + systemId + ": " + e.getMessage(), e );
        }
    }

    /**
     * Called for parsing or validating a single file.
     * 
     * @param pResolver The resolver to use for loading external entities.
     * @param pValidationSet The parsers or validators configuration.
     * @param pSchema The schema to use.
     * @param pFile The file to parse or validate.
     * @throws MojoExecutionException Parsing or validating the file failed.
     */
    private void validate( final Resolver pResolver, ValidationSet pValidationSet, Schema pSchema, File pFile )
        throws MojoExecutionException
    {
        try
        {
            if ( pSchema == null && pValidationSet.isAutodetect()==false )
            {
                getLog().debug( "-> Parsing " + pFile.getPath() );
                parse( pResolver, pValidationSet, pFile );
            }
            else
            {
                getLog().debug( "-> Validating " + pFile.getPath() );
                //Clone validatingSet for safety modify
                ValidationSet currentSet = null;
            	try {
        			currentSet = (ValidationSet) pValidationSet.clone();
        		} catch (CloneNotSupportedException e) {
        			throw new IllegalStateException("Can't get ValidationSet for work.");
        		}
                
                Validator validator = null;
                if (pSchema != null){
                	validator = pSchema.newValidator();
                }else{
                	//Get data from file to current Validation Set
                	XmlUrlReader reader = new XmlUrlReader( pFile.toURI().toURL(), getLog() );
                    NameSpaceDetector xmlNameSpace = new NameSpaceDetector();
                    reader.addDetector( xmlNameSpace );
                    reader.addDetector( new DTDdetector(currentSet) );
                    reader.addDetector( new XSDdetector(currentSet) );
                    try{
                    	reader.read();
                    }catch(NoSuchElementException e){
                    	getLog().warn("Empty file: " + pFile.getAbsolutePath());
                    }
                    //If we have DTD schema file - validate as DTD
                	if ( currentSet.getSystemId() != null && currentSet.getSystemId().contains(".dtd")){
                		doDTDvalidation( pFile );
                        return;
                	}
                	if (currentSet.getSystemId()==null){
                		//getLog().warn("Can't find schema systemId. Skipped.");
                		return;
                	}
                	
                	//Try to compare nameSpace from document and schema (info can help for understanding of error
                	try{
                		if (currentSet.getSystemId()!=null){
                			NameSpaceDetector schemaNameSpace = new NameSpaceDetector();
                			reader = new XmlUrlReader( new URL(currentSet.getSystemId()), getLog() );
                			reader.addDetector( schemaNameSpace );
                			reader.read();
                			if ( !schemaNameSpace.getNameSpace().equals( xmlNameSpace.getNameSpace() )){
                				getLog().error("WARNING!!! NameSpace in schema file differ from NameSpace in file" + pFile.getAbsolutePath());
                			}
                		}
                    	validator = getSchema(pResolver, currentSet).newValidator();
                	}catch(MalformedURLException e){
                		getLog().warn( e.getMessage() + " in "+ pFile.getAbsolutePath() );
                	}catch(IllegalStateException e){
                		getLog().warn( e.getMessage() + " in "+ pFile.getAbsolutePath() );
                	}
                }
                if (validator == null){
                	return;
                }
                
                if ( pResolver != null )
                {
                    validator.setResourceResolver( pResolver );
                }
                validator.validate( new StreamSource( pFile ) );
            }
        }
        catch ( SAXParseException e )
        {
            final String publicId = e.getPublicId();
            final String systemId = e.getSystemId();
            final int lineNum = e.getLineNumber();
            final int colNum = e.getColumnNumber();
            final String location;
            if ( publicId == null && systemId == null && lineNum == -1 && colNum == -1 )
            {
                location = "";
            }
            else
            {
                final StringBuffer loc = new StringBuffer();
                String sep = "";
                if ( publicId != null )
                {
                    loc.append( "Public ID " );
                    loc.append( publicId );
                    sep = ", ";
                }
                if ( systemId != null )
                {
                    loc.append( sep );
                    loc.append( systemId );
                    sep = ", ";
                }
                if ( lineNum != -1 )
                {
                    loc.append( sep );
                    loc.append( "line " );
                    loc.append( lineNum );
                    sep = ", ";
                }
                if ( colNum != -1 )
                {
                    loc.append( sep );
                    loc.append( " column " );
                    loc.append( colNum );
                    sep = ", ";
                }
                location = loc.toString();
            }
            final String msg = "While parsing " + pFile.getPath() + ( "".equals( location ) ? "" : ", at " + location )
                + ": " + e.getMessage();
            throw new MojoExecutionException( msg, e );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "While parsing " + pFile + ": " + e.getMessage(), e );
        }
    }

    /**
     * Creates a new instance of {@link SAXParserFactory}.
     * 
     * @param pValidationSet The parser factories configuration.
     * @return A new SAX parser factory.
     */
    private SAXParserFactory newSAXParserFactory( ValidationSet pValidationSet )
    {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating( pValidationSet.isValidating() );
        if ( pValidationSet.isValidating() )
        {
            try
            {
                spf.setFeature( "http://apache.org/xml/features/validation/schema", true );
            }
            catch ( SAXException e )
            {
                // Ignore this
            }
            catch ( ParserConfigurationException e )
            {
                // Ignore this
            }
        }
        else
        {
            try
            {
                spf.setFeature( "http://apache.org/xml/features/nonvalidating/load-external-dtd", false );
            }
            catch ( SAXException e )
            {
                // Ignore this
            }
            catch ( ParserConfigurationException e )
            {
                // Ignore this
            }
        }
        spf.setNamespaceAware( true );
        return spf;
    }

    /**
     * Called for validating a single file.
     * 
     * @param pResolver The resolver to use for loading external entities.
     * @param pValidationSet The validators configuration.
     * @param pFile The file to validate.
     * @throws IOException An I/O error occurred.
     * @throws SAXException Parsing the file failed.
     * @throws ParserConfigurationException Creating an XML parser failed.
     */
    private void parse( Resolver pResolver, ValidationSet pValidationSet, File pFile )
        throws IOException, SAXException, ParserConfigurationException
    {
        XMLReader xr = newSAXParserFactory( pValidationSet ).newSAXParser().getXMLReader();
        if ( pResolver != null )
        {
            xr.setEntityResolver( pResolver );
        }
        xr.setErrorHandler( new ErrorHandler()
        {
            @Override
			public void error( SAXParseException pException )
                throws SAXException
            {
                throw pException;
            }

            @Override
			public void fatalError( SAXParseException pException )
                throws SAXException
            {
                throw pException;
            }

            @Override
			public void warning( SAXParseException pException )
                throws SAXException
            {
                throw pException;
            }

        } );
        xr.parse( pFile.toURI().toURL().toExternalForm() );
    }

    /**
     * Called for validating a set of XML files against a common schema.
     * 
     * @param pResolver The resolver to use for loading external entities.
     * @param pValidationSet The set of XML files to validate.
     * @throws MojoExecutionException Validating the set of files failed.
     * @throws MojoFailureException A configuration error was detected.
     */
    private void validate( Resolver pResolver, ValidationSet pValidationSet )
        throws MojoExecutionException, MojoFailureException
    {
        final Schema schema = getSchema( pResolver, pValidationSet );
        final File[] files =
            getFiles( pValidationSet.getDir(), pValidationSet.getIncludes(),
                      getExcludes( pValidationSet.getExcludes(), pValidationSet.isSkipDefaultExcludes() ) );
        if ( files.length == 0 )
        {
            getLog().info( "No matching files found for ValidationSet with public ID " + pValidationSet.getPublicId()
                + ", system ID " + pValidationSet.getSystemId() + "." );
        }
        for ( int i = 0; i < files.length; i++ )
        {
            validate( pResolver, pValidationSet, schema, files[i] );
        }
    }

    /**
     * Called by Maven for executing the Mojo.
     * 
     * @throws MojoExecutionException Running the Mojo failed.
     * @throws MojoFailureException A configuration error was detected.
     */
    @Override
	public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( isSkipping() )
        {
            getLog().debug( "Skipping execution, as demanded by user." );
            return;
        }

        if ( validationSets == null || validationSets.length == 0 )
        {
            throw new MojoFailureException( "No ValidationSets configured." );
        }

        Object oldProxySettings = activateProxy();
        try
        {
            Resolver resolver = getResolver();
            for ( int i = 0; i < validationSets.length; i++ )
            {
                ValidationSet validationSet = validationSets[i];
                resolver.setValidating( validationSet.isValidating() );
                validate( resolver, validationSet );
            }
        }
        finally
        {
            passivateProxy( oldProxySettings );
        }
    }
    
	/**
	 * Perform <a href=http://www.w3schools.com/xml/xml_dtd.asp>DTD</a> validation for XML document.<br>
	 * An example: <a href=https://docs.oracle.com/javase/tutorial/jaxp/sax/validation.html>Validation example</a>
	 * 
	 * @param file
	 *            XML document for validation.
	 */
	public void doDTDvalidation(File file) {
		getLog().debug("Process DTD validation...");

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(true);

		XMLReader reader;
		try {
			reader = factory.newSAXParser().getXMLReader();
		} catch (SAXException e) {
			throw new IllegalStateException("Can't get reader for xml file");
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("File parsing error");
		}

		reader.setErrorHandler(new ErrorHandler() {
			@Override
			public void warning(SAXParseException exception) throws SAXException {
				getLog().warn(exception);
			}

			@Override
			public void error(SAXParseException exception) throws SAXException {
				throw new IllegalStateException("Validation failed:" + exception.getMessage());
			}

			@Override
			public void fatalError(SAXParseException exception) throws SAXException {
				throw new IllegalStateException("Validation failed:" + exception.getMessage());
			}

		});

		try {
			reader.parse(new InputSource(file.getAbsolutePath()));
		} catch (IOException e) {
			throw new IllegalStateException("I/O error: ", e);
		} catch (SAXException e) {
			throw new IllegalStateException(e);
		}
	}	
}
