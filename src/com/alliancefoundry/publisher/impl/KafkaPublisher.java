package com.alliancefoundry.publisher.impl;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.alliancefoundry.model.Event;
import com.alliancefoundry.publisher.EventServicePublisher;
import com.alliancefoundry.publisher.PublisherInterface;
import com.alliancefoundry.publisher.RouterConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

public class KafkaPublisher implements PublisherInterface {

	private Producer<String, String> producer;
	private String brokerUrl;
	private String destType;

	
	public KafkaPublisher() {
		
	}
	
	@Override
	public void connect() {
		Properties props = new Properties();

		props.put("metadata.broker.list", brokerUrl);
		props.put("serializer.class", "kafka.serializer.StringEncoder");
		props.put("request.required.acks", "1");

		ProducerConfig config = new ProducerConfig(props);
		producer = new Producer<String, String>(config);
	}
	
	public String getBrokerUrl() {
		return brokerUrl;
	}

	public void setBrokerUrl(String brokerUrl) {
		this.brokerUrl = brokerUrl;
	}

	
	@Override
	public void publishEvent(Event event, RouterConfig eventConfig) {
		
		ObjectMapper mapper = new ObjectMapper();
		  mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // no more null-valued properties
		    mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
		    mapper.setSerializationInclusion(Include.NON_NULL);

		String jsonEvent = null;
		
		try {
			jsonEvent = mapper.writeValueAsString(event);
		} 
		catch (JsonProcessingException e) {
			System.out.println("Error converting object to JSON String.");
		}

		String topic =   eventConfig.getDestination(event.getMessageType());
		KeyedMessage<String, String> jsonData = new KeyedMessage<String, String>(topic, jsonEvent);
		producer.send(jsonData);
				
		producer.close();

	}
	
	@Override
	public void publishEvent(List<Event> events, RouterConfig eventConfig) {

		
	}
	


}

