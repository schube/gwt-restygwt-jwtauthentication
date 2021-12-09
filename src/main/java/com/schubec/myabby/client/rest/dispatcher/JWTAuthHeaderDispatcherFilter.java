package com.schubec.myabby.client.rest.dispatcher;

import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.dispatcher.DispatcherFilter;

import com.google.gwt.http.client.RequestBuilder;
import com.schubec.myabby.client.rest.models.UserCredentials;

final public class JWTAuthHeaderDispatcherFilter implements DispatcherFilter {

	public static final String AUTHORIZATION_HEADER = "Authorization";

	@Override
	public boolean filter(Method method, RequestBuilder builder) {
		if (UserCredentials.INSTANCE.getAccessToken() != null) {
			builder.setHeader(AUTHORIZATION_HEADER, "Bearer " + UserCredentials.INSTANCE.getAccessToken());
		}
		return true;
	}

}
