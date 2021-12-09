package com.schubec.myabby.client.rest.dispatcher;

import org.fusesource.restygwt.client.dispatcher.DefaultFilterawareDispatcher;
import org.gwtproject.event.shared.SimpleEventBus;

public class JWTDispatcher extends DefaultFilterawareDispatcher {

	public JWTDispatcher(SimpleEventBus eventBus) {
		addFilter(new ForbiddenDispatcherFilter(eventBus));

		addFilter(new JWTAuthHeaderDispatcherFilter());

	}

}
