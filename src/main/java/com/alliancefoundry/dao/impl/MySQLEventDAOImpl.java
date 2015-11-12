package com.alliancefoundry.dao.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.PreparedStatement;


import javax.sql.DataSource;

import com.alliancefoundry.dao.DAOException;
import com.alliancefoundry.dao.EventDAO;
import com.alliancefoundry.model.*;
import org.joda.time.DateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQLEventDAOImpl implements EventDAO {

	private DataSource dataSource;
	private static final Logger log = LoggerFactory.getLogger(MySQLEventDAOImpl.class);

	public MySQLEventDAOImpl(DataSource dataSource){
		this.dataSource = dataSource;
	}

	public boolean initialize() throws DAOException {

		return true;
	}

	/**
	 * Deallocate the DAO resources.
	 *
	 * @return true if resources are releases successfully
	 * @throws DAOException
	 */
	public boolean shutdown() throws DAOException{
		return true;
	}



	//Insert an Event object into the database using a prepared statement
	//and return the event id of the Event object that was inserted.
	//Returns null if insert failed.
	@Override
	public EventResponse insertEvent(Event event) throws DAOException {


		Connection conn = null;


		String sql = "INSERT INTO event_store VALUES ( ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? )";
		String headersSql = "INSERT INTO event_headers VALUES ( ?,?,? )";
		String payloadSql = "INSERT INTO event_payload VALUES ( ?,?,?,? )";

		try {

			conn = dataSource.getConnection();

			String eventId = event.getEventId();
			PreparedStatement ps = conn.prepareStatement(sql);
			//set the value of each column for the row being inserted other
			//than eventId
			ps.setString(1, eventId);
			ps.setString(2, event.getParentEventId());
			ps.setString(3, event.getEventName());
			ps.setString(4, event.getObjectId());
			ps.setString(5, event.getCorrelationId());

			Integer seqNum = event.getSequenceNumber();
			if(seqNum != null) {
				ps.setInt(6, seqNum);
			} else {
				ps.setNull(6, 0);
			}

			ps.setString(7, event.getMessageType());
			ps.setString(8, event.getDataType());
			ps.setString(9, event.getSource());
			ps.setString(10, event.getDestination());
			ps.setString(11, event.getSubdestination());

			Boolean replay = event.isReplayIndicator();
			if(replay != null) {
				ps.setBoolean(12, replay);
			} else {
				ps.setNull(12, 0);
			}

			DateTime pubTime = event.getPublishTimeStamp();
			if(pubTime != null){
				ps.setLong(13, pubTime.getMillis());
			} else {
				ps.setNull(13, 0);
			}

			ps.setLong(14, event.getReceivedTimeStamp().getMillis());

			DateTime expTime = event.getExpirationTimeStamp();
			if(expTime != null){
				ps.setLong(15, expTime.getMillis());
			} else {
				ps.setNull(15, 0);
			}

			ps.setString(16, event.getPreEventState());
			ps.setString(17, event.getPostEventState());

			Boolean publish = event.isPublishable();
			if(publish != null){
				ps.setBoolean(18, replay);
			} else {
				ps.setNull(18, 0);
			}

			event.setInsertTimeStamp(DateTime.now());
			ps.setLong(19, event.getInsertTimeStamp().getMillis());

			ps.executeUpdate();

			PreparedStatement headersPs = conn.prepareStatement(headersSql);

			//insert header info into its table
			for(String key : event.getCustomHeaders().keySet()){
				headersPs.setString(1, eventId);
				headersPs.setString(2, key);
				headersPs.setString(3, event.getCustomHeaders().get(key));
				headersPs.executeUpdate();
			}

			PreparedStatement payloadPs = conn.prepareStatement(payloadSql);

			//insert payload info into its table
			for(String key : event.getCustomPayload().keySet()){
				payloadPs.setString(1, eventId);
				payloadPs.setString(2, key);
				payloadPs.setString(3, event.getCustomPayload().get(key).getValue());
				payloadPs.setString(4, event.getCustomPayload().get(key).getDataType().name());
				payloadPs.executeUpdate();
			}

			return new EventResponse(event);
		} catch (SQLException e) {
			throw new DAOException(e);
		} finally{
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
					log.error("connection could not be closed with error. " + e.getMessage());
				}
			}

		}
	}

	@Override
	public EventsResponse insertEvents(List<Event> events) throws DAOException {
		return null;
	}

	//Retrieve an event from the database into an Event object
	//that is returned.  Return null if the requested Event
	//object is not found in the database.
	@Override
	public EventResponse getEvent(String eventId) throws DAOException {


		Connection conn = null;

		String sql = "SELECT * FROM event_store WHERE eventId = ?";
		String headersSql = "SELECT name,value FROM event_headers WHERE eventId = ?";
		String payloadSql = "SELECT name,value,dataType FROM event_payload WHERE eventId = ?";

		try {

			conn = dataSource.getConnection();

			PreparedStatement ps = conn.prepareStatement(sql);
			
			//set the value of eventId being checked for equality
			ps.setString(1, eventId);
			
			ResultSet rs = ps.executeQuery();
			Event event;
			
			//get to start of resultSet
			rs.next();
			
			DateTime publish = new DateTime(rs.getLong("publishTimeStamp"));
			if (rs.wasNull()) publish = null;
			DateTime expiration = new DateTime(rs.getLong("expirationTimeStamp"));
			if (rs.wasNull()) expiration = null;
			
			event = new Event(
					rs.getString("parentId"),
					rs.getString("eventName"),
					rs.getString("objectId"),
					rs.getString("correlationId"),
					rs.getInt("sequenceNumber"),
					rs.getString("messageType"),
					rs.getString("dataType"),
					rs.getString("source"),
					rs.getString("destination"),
					rs.getString("subdestination"),
					rs.getBoolean("replayIndicator"),
					publish,
					new DateTime(rs.getLong("receivedTimeStamp")),
					expiration,
					rs.getString("preEventState"),
					rs.getString("postEventState"),
					rs.getBoolean("isPublishable"),
					new DateTime(rs.getLong("insertTimeStamp"))
				);
			event.setEventId(rs.getString("eventId"));
			
			PreparedStatement psHeaders = (PreparedStatement) conn.prepareStatement(headersSql);
			
			//set the value being checked for equality
			psHeaders.setString(1, eventId);
			
			ResultSet rsHeaders = psHeaders.executeQuery();
			Map<String,String> customHeaders = new HashMap<String,String>();
			
			//get header info from its table
			while(rsHeaders.next()){
				customHeaders.put(rsHeaders.getString("name"), rsHeaders.getString("value"));
			}
			
			PreparedStatement psPayload = conn.prepareStatement(payloadSql);
			
			//set the value being checked for equality
			psPayload.setString(1, eventId);
			
			ResultSet rsPayload = psPayload.executeQuery();
			Map<String,DataItem> customPayload = new HashMap<String,DataItem>();
			
			//get payload info from its table
			while(rsPayload.next()){
				String payName = rsPayload.getString("name");
				String payType = rsPayload.getString("dataType");
				String payVal = rsPayload.getString("value");

				PrimitiveDatatype v = null;
				if (payType.equalsIgnoreCase("boolean")) { v = PrimitiveDatatype.Boolean; }
				if (payType.equalsIgnoreCase("byte")) { v = PrimitiveDatatype.Byte; }
				if (payType.equalsIgnoreCase("double")) { v = PrimitiveDatatype.Double; }
				if (payType.equalsIgnoreCase("float")) { v = PrimitiveDatatype.Float; }
				if (payType.equalsIgnoreCase("integer")) { v = PrimitiveDatatype.Integer; }
				if (payType.equalsIgnoreCase("long")) { v = PrimitiveDatatype.Long; }
				if (payType.equalsIgnoreCase("short")) { v = PrimitiveDatatype.Short; }
				if (payType.equalsIgnoreCase("string")) { v = PrimitiveDatatype.String; }
				if (v==null) { v = PrimitiveDatatype.String; }



				customPayload.put(payName, new DataItem(v,payVal));
			}
			
			event.setCustomHeaders(customHeaders);
			event.setCustomPayload(customPayload);
			return new EventResponse(event);
		} catch (SQLException e) {
			//event couldn't be retrieved
			return null;
		} finally{
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {}
			}
		}
	}
	
	// Retrieve multiple events from the database based off of an EventsRequest object
	//into a list of Event objects that is returned.
	@Override
	public EventsResponse getEvents(List<String> req) throws DAOException {

		return null;
	}
}
