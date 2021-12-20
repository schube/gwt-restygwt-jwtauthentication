package com.schubec.myabby.client.rest.dispatcher;

import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;
import org.gwtproject.event.shared.SimpleEventBus;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.logging.client.LogConfiguration;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Timer;
import com.schubec.spass.client.CookieKeys;
import com.schubec.spass.client.event.RuntimeErrorEvent;
import com.schubec.spass.client.rest.LoginService;
import com.schubec.spass.client.rest.ServiceFactory;
import com.schubec.spass.client.rest.models.JWTToken;
import com.schubec.spass.client.rest.models.UserCredentials;

import elemental2.promise.Promise;

/**
 * Based on
 * https://stackoverflow.com/questions/35349799/restygwt-custom-dispatcher-doesnt-call-registered-filters
 * and RetryingFilterawareRequestCallback
 */
public class RefreshTokenDispatcherCallback implements RequestCallback {

	protected RequestCallback requestCallback;
	private Method mainmethod;
	private SimpleEventBus eventBus;
	private static Promise<String> accessTokenPromise;
	private static boolean accesstokenIsBeiingRefreshed = false;

	public RefreshTokenDispatcherCallback(Method method, SimpleEventBus eventBus) {
		// GWT.log("_________> ForbiddenDispatcherCallback " + method.toString());
		this.mainmethod = method;
		this.requestCallback = method.builder.getCallback();
		this.eventBus = eventBus;
	}

	@Override
	public void onResponseReceived(Request request, Response response) {
		if (response.getStatusCode() == Response.SC_OK) {
			requestCallback.onResponseReceived(request, response);
		} else if (response.getStatusCode() == Response.SC_UNAUTHORIZED && accesstokenIsBeiingRefreshed == true) {
			GWT.log("Another request is getting a new AccessToken. Delaying request [" + mainmethod.builder.getHTTPMethod() + "/" + mainmethod.builder.getUrl() + "] until promise is resolved.");

			accessTokenPromise.then(newlyRefreshedAccessToken -> {
				GWT.log("Executing delayed request [" + mainmethod.builder.getHTTPMethod() + "/" + mainmethod.builder.getUrl() + "] now.");
				try {
					// Rewrite the Authorization Header...
					mainmethod.header("Authorization", "Bearer " + newlyRefreshedAccessToken);
					// ...and send it again!
					mainmethod.builder.send();
				} catch (RequestException ex) {
					if (GWT.isClient() && LogConfiguration.loggingIsEnabled()) {
						GWT.log("Error sending delayed request", ex);
					}
				}
				return null;
			}, (error) -> {
				GWT.log("waiting for new accessTokenPromise rejected");
				// We don't have to do much here, since we already fired a RuntimeErrorEvent
				// when fetching a new access token failed.
				return null;
			});

		} else if (response.getStatusCode() == Response.SC_UNAUTHORIZED && accesstokenIsBeiingRefreshed == false) {
			// Token erneuern
			accessTokenPromise = new Promise<>((resolve, reject) -> {
				accesstokenIsBeiingRefreshed = true;
				GWT.log("_________> RefreshTokenDispatcherCallback -> onResponseReceived -> Fetching new Access Token");
				LoginService loginservice = ServiceFactory.getLoginService();
				loginservice.refreshtoken(UserCredentials.INSTANCE.getRefreshToken(), new MethodCallback<JWTToken>() {

					@Override
					public void onSuccess(Method method, JWTToken jwttoken) {
						accesstokenIsBeiingRefreshed = false;
						GWT.log("_________> ForbiddenDispatcherCallback -> onResponseReceived -> Hole neuen JWT -> success");
						UserCredentials.store(jwttoken);
						resolve.onInvoke(jwttoken.getAccessToken());
						try {
							// Rewrite the Authorization Header...
							mainmethod.header("Authorization", "Bearer " + jwttoken.getAccessToken());
							// ...and send it again!
							mainmethod.builder.send();
						} catch (RequestException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}

					@Override
					public void onFailure(Method method, Throwable exception) {
						accesstokenIsBeiingRefreshed = false;
						// GWT.log("_________> ForbiddenDispatcherCallback -> onResponseReceived -> Hole
						// neuen JWT -> error");

						eventBus.fireEvent(new RuntimeErrorEvent("Authentifizierungsfehler", "Ihre Session ist abgelaufen. Bitte loggen Sie sich erneut ein.", true));
						Cookies.removeCookie(CookieKeys.JWT_REFRESH_TOKEN);
						reject.onInvoke(exception);
					}
				});
			});
		} else {
			GWT.log("onResponseReceived with Statuscode: " + response.getStatusCode());
			// GWT.log("onResponseReceived with Text: " + response.getText());
			requestCallback.onResponseReceived(request, response);
		}

	}

	@Override
	public void onError(Request request, Throwable exception) {
		// GWT.log("_________> ForbiddenDispatcherCallback -> onError");
		requestCallback.onError(request, exception);
	}

}
