package com.schubec.myabby.client.rest.dispatcher;

import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.dispatcher.DispatcherFilter;
import org.gwtproject.event.shared.SimpleEventBus;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.RequestBuilder;

public class ForbiddenDispatcherFilter implements DispatcherFilter {
	
    private SimpleEventBus eventBus;

	public ForbiddenDispatcherFilter(SimpleEventBus eventBus) {
		this.eventBus = eventBus;
	}

	@Override
    public boolean filter(Method method, RequestBuilder builder) {
    	GWT.log("--> ForbiddenDispatcherFilter -> filter -> setCallback");
    	builder.setCallback(new RefreshTokenDispatcherCallback(method, eventBus));
    	return true;
    }

}
