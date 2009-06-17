package gov.nist.registry.ws;

import gov.nist.registry.common2.MetadataTypes;
import gov.nist.registry.common2.exception.ExceptionUtil;
import gov.nist.registry.common2.exception.MetadataException;
import gov.nist.registry.common2.exception.MetadataValidationException;
import gov.nist.registry.common2.exception.SchemaValidationException;
import gov.nist.registry.common2.exception.XDSRegistryOutOfResourcesException;
import gov.nist.registry.common2.exception.XdsException;
import gov.nist.registry.common2.exception.XdsFormatException;
import gov.nist.registry.common2.exception.XdsInternalException;
import gov.nist.registry.common2.exception.XdsValidationException;
import gov.nist.registry.common2.exception.XdsWSException;
import gov.nist.registry.common2.registry.AdhocQueryResponse;
import gov.nist.registry.common2.registry.BackendRegistry;
import gov.nist.registry.common2.registry.BasicQuery;
import gov.nist.registry.common2.registry.Metadata;
import gov.nist.registry.common2.registry.MetadataParser;
import gov.nist.registry.common2.registry.MetadataSupport;
import gov.nist.registry.common2.registry.RegistryUtility;
import gov.nist.registry.common2.registry.Response;
import gov.nist.registry.common2.registry.XdsCommon;
import gov.nist.registry.xdslog.LoggerException;
import gov.nist.registry.xdslog.Message;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.log4j.Logger;

public class AdhocQueryRequest extends XdsCommon {
	MessageContext messageContext;
	String service_name = "";
	boolean is_secure;
	private final static Logger logger = Logger.getLogger(AdhocQueryRequest.class);


	public AdhocQueryRequest(Message log_message, MessageContext messageContext, boolean is_secure, short xds_version) {
		this.log_message = log_message;
		this.messageContext = messageContext;
		this.is_secure = is_secure;
		this.xds_version = xds_version;
	}
	
	public void setServiceName(String service_name) {
		this.service_name = service_name;
	}

	public OMElement adhocQueryRequest(OMElement ahqr) {
		ahqr.build();

		OMNamespace ns = ahqr.getNamespace();
		String ns_uri = ns.getNamespaceURI();

		try {
			if (ns_uri.equals(MetadataSupport.ebQns3.getNamespaceURI())) {
				init(new AdhocQueryResponse(Response.version_3), xds_version, messageContext);
			} else if (ns_uri.equals(MetadataSupport.ebQns2.getNamespaceURI())) {
				init(new AdhocQueryResponse(Response.version_2), xds_version, messageContext);
			} else {
				init(new AdhocQueryResponse(Response.version_3), xds_version, messageContext);
				response.add_error("XDSRegistryError", "Invalid XML namespace on AdhocQueryRequest: " + ns_uri, "AdhocQueryRequest.java", log_message);
				return response.getResponse();
			}
		} catch (XdsInternalException e) {
			logger.fatal("Internal Error initializing AdhocQueryRequest transaction: " + e.getMessage());
			return null;
		}

		try {
			mustBeSimpleSoap();

			AdhocQueryRequestInternal(ahqr);

		} 
		catch (XdsValidationException e) {
			response.add_error("XDSRegistryError", "Validation Error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
		} 
		catch (XdsFormatException e) {
			response.add_error("XDSRegistryError", "SOAP Format Error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
		} 
		catch (XDSRegistryOutOfResourcesException e) {
			// query return limitation
			response.add_error("XDSRegistryOutOfResources", e.getMessage(), RegistryUtility.exception_trace(e), log_message);
		} 
		catch (SchemaValidationException e) {
			response.add_error("XDSRegistryMetadataError", "SchemaValidationException: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
		} 
		catch (XdsInternalException e) {
			response.add_error("XDSRegistryError", "Internal Error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
			logger.fatal(logger_exception_details(e));
		} 
		catch (MetadataValidationException e) {
			response.add_error("XDSRegistryError", "Metadata Error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
		}  
		catch (SqlRepairException e) {
			response.add_error("XDSRegistryError", "Could not decode SQL: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
			logger.warn(logger_exception_details(e));
		}  
		catch (MetadataException e) {
			response.add_error("XDSRegistryError", "Metadata error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
		}  
		catch (LoggerException e) {
			response.add_error("XDSRegistryError", "Logger error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
			logger.fatal(logger_exception_details(e));
		}  
		catch (SQLException e) {
			response.add_error("XDSRegistryError", "SQL error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
			logger.fatal(logger_exception_details(e));
		}  
		catch (XdsException e) {
			response.add_error("XDSRegistryError", "XDS Error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
			logger.warn(logger_exception_details(e));
		}  
		catch (Exception e) {
			response.add_error("General Exception", "Internal Error: " + e.getMessage(), RegistryUtility.exception_trace(e), log_message);
			logger.fatal(logger_exception_details(e));
		}  

		this.log_response();

		OMElement res = null;
		try {
			res =  response.getResponse();
		} catch (XdsInternalException e) {

		}
		return res;
	}

	public boolean isStoredQuery(OMElement ahqr) {
		for (Iterator it=ahqr.getChildElements(); it.hasNext(); ) {
			OMElement ele = (OMElement) it.next();
			String ele_name = ele.getLocalName();

			if (ele_name.equals("AdhocQuery")) 
				return true;
		}
		return false;
	}

	void AdhocQueryRequestInternal(OMElement ahqr) 
	throws SQLException, XdsException, LoggerException, XDSRegistryOutOfResourcesException, AxisFault, 
	SqlRepairException, XdsValidationException {

		boolean found_query = false;

		for (Iterator it=ahqr.getChildElements(); it.hasNext(); ) {
			OMElement ele = (OMElement) it.next();
			String ele_name = ele.getLocalName();

			if (ele_name.equals("SQLQuery")) {
				log_message.setTestMessage("SQL");
				RegistryUtility.schema_validate_local(ahqr, MetadataTypes.METADATA_TYPE_Q);
				found_query=true;
				OMElement result =  sql_query(ahqr);
				// move result elements to response
				if (result != null) {
					Metadata m = new Metadata(result, false /* parse */, true /* find_wrapper */);
					OMElement sqr = m.getWrapper();
					if (sqr != null) {
						for (Iterator it2=sqr.getChildElements(); it2.hasNext(); ) {
							OMElement e = (OMElement) it2.next();
							((AdhocQueryResponse)response).addQueryResults(e);
						}
					}
				}
			} else if (ele_name.equals("AdhocQuery")) {
				log_message.setTestMessage(service_name);

				RegistryUtility.schema_validate_local(ahqr, MetadataTypes.METADATA_TYPE_SQ);
				found_query = true;

				ArrayList<OMElement> results =  stored_query(ahqr);


				//response.query_results = results;
				if (results != null)
					((AdhocQueryResponse)response).addQueryResults(results);
			} 

		}
		if (!found_query)
			response.add_error("XDSRegistryError", "Only SQLQuery and AdhocQuery accepted",  "AdhocQueryRequest.java", log_message);

		this.log_response();

	}


	public String getStoredQueryId(OMElement ahqr) {
		OMElement adhoc_query = MetadataSupport.firstChildWithLocalName(ahqr, "AdhocQuery") ;
		if (adhoc_query == null) return null;
		return adhoc_query.getAttributeValue(MetadataSupport.id_qname);
	}

	public String getHome(OMElement ahqr) throws XdsInternalException, MetadataException, XdsException, LoggerException {
		OMElement adhocQuery = MetadataSupport.firstChildWithLocalName(ahqr, "AdhocQuery");
		return adhocQuery.getAttributeValue(MetadataSupport.home_qname);
//		return (String) new StoredQueryFactory(ahqr).getParm("$homeCommunityId");
		
//		OMElement ahquery = MetadataSupport.firstChildWithLocalName(ahqr, "AdhocQuery");
//		if (ahquery == null) return null;
//		return ahquery.getAttributeValue(MetadataSupport.id_qname);
	}

	// Initiating Gateway shall specify the homeCommunityId attribute in all Cross-Community 
	// Queries which do not contain a patient identifier.
	public boolean requiresHomeInXGQ(OMElement ahqr) {
		boolean requires = true;
		String query_id = getStoredQueryId(ahqr);
		if (query_id == null) requires = false;
		if (query_id.equals(MetadataSupport.SQ_FindDocuments)) requires = false; 
		if (query_id.equals(MetadataSupport.SQ_FindFolders)) requires = false; 
		if (query_id.equals(MetadataSupport.SQ_FindSubmissionSets)) requires = false; 
		if (query_id.equals(MetadataSupport.SQ_GetAll)) requires = false; 
		logger.info("query " + query_id + " requires home = " + requires);
		return requires;
	}

	ArrayList<OMElement> stored_query(OMElement ahqr) 
	throws XdsException, LoggerException, XDSRegistryOutOfResourcesException, XdsValidationException {
		
		
//		new StoredQueryRequestSoapValidator(xds_version, messageContext).runWithException();

		try {
			StoredQueryFactory fact = new StoredQueryFactory(ahqr);
			fact.setServiceName(service_name);
			fact.setLogMessage(log_message);
			fact.setIsSecure(is_secure);
			fact.setResponse(response);
			return fact.run();
		}
		catch (Exception e) {
			response.add_error("XDSRegistryError", ExceptionUtil.exception_details(e), "StoredQueryFactory.java", log_message);
			return null;

		}

	}


	private OMElement sql_query(OMElement ahqr) 
	throws XdsInternalException, SchemaValidationException, LoggerException, SqlRepairException, MetadataException, MetadataValidationException {
		// validate against schema - throws exception on error

		RegistryUtility.schema_validate_local(ahqr, MetadataTypes.METADATA_TYPE_Q);

		System.out.println("sql_query");
		// check and repair SQL

		SqlRepair sr = new SqlRepair();
		String return_type = sr.returnType(ahqr);
		try {
			sr.repair(ahqr);
		} catch (SqlRepairException e) {
			response.add_error("XDSSqlError", e.getMessage(),  RegistryUtility.exception_details(e), log_message);
			return null;
		} catch (XdsInternalException e) {
			response.add_error("XDSRegistryError", e.getMessage(), RegistryUtility.exception_details(e), log_message);
			return null;
		}

		if (log_message != null)
			log_message.addOtherParam("SQL", sr.get_query_text(ahqr));

		System.out.println("SQLQuery:\n" + sr.get_query_text(ahqr));

		//return backend_query(ahqr);

		BackendRegistry br = new BackendRegistry(response, log_message);
		OMElement results = br.query(ahqr);

		Metadata metadata = MetadataParser.parseNonSubmission(results);
		if (is_secure) {
			BasicQuery bq = new BasicQuery();
			bq.secure_URI(metadata);
		}

		// Problem here - ebxmlrr returns wrong namespaces.  The following fixes
		results = metadata.fixSQLQueryResponse(return_type);

		return results;
	}


}