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
import com.schubec.myabby.client.CookieKeys;
import com.schubec.myabby.client.event.RuntimeErrorEvent;
import com.schubec.myabby.client.rest.LoginService;
import com.schubec.myabby.client.rest.ServiceFactory;
import com.schubec.myabby.client.rest.models.JWTToken;
import com.schubec.myabby.client.rest.models.UserCredentials;

/**
 * Based on
 * https://stackoverflow.com/questions/35349799/restygwt-custom-dispatcher-doesnt-call-registered-filters
 * and RetryingFilterawareRequestCallback
 */
public class RefreshTokenDispatcherCallback implements RequestCallback {

	protected RequestCallback requestCallback;
	private Method mainmethod;
	private SimpleEventBus eventBus;
	private static boolean accesstokenIsBeiingRefreshed = false;
	protected int gracePeriod = 100;
	protected int numberOfRetries = 10;
	protected int currentRetryCounter = 0;

	public RefreshTokenDispatcherCallback(Method method, SimpleEventBus eventBus) {
		// GWT.log("_________> ForbiddenDispatcherCallback " + method.toString());
		this.mainmethod = method;
		this.requestCallback = method.builder.getCallback();
		this.eventBus = eventBus;
	}

	@Override
	public void onResponseReceived(Request request, Response response) {
		// GWT.log("_________> ForbiddenDispatcherCallback -> onResponseReceived");

		// GWT.log(response.getStatusText() + "/"+ response.getStatusCode() + ". Request
		// was " + request.toString());
		if (response.getStatusCode() == Response.SC_OK) {
			// GWT.log("onResponseReceived with Statuscode 200/OK");
			requestCallback.onResponseReceived(request, response);
		} else if (response.getStatusCode() == Response.SC_UNAUTHORIZED && accesstokenIsBeiingRefreshed == true) {
			GWT.log("Another request is getting a new AccessToken. Delaying request [" + mainmethod.builder.getHTTPMethod() + "/" + mainmethod.builder.getUrl() + "] for [" + gracePeriod + "]ms.");

			// We will delay our request for and hope, the other request fetched the token
			// already.
			Timer t = new Timer() {
				@Override
				public void run() {
					if (currentRetryCounter < numberOfRetries) {
						currentRetryCounter++;
						if (accesstokenIsBeiingRefreshed == false) {
							GWT.log("Executing delayed request [" + mainmethod.builder.getHTTPMethod() + "/" + mainmethod.builder.getUrl() + "] now.");
							try {
								// Rewrite the Authorization Header...
								mainmethod.header("Authorization", "Bearer " + UserCredentials.INSTANCE.getAccessToken());
								// ...and send it again!
								mainmethod.builder.send();
							} catch (RequestException ex) {
								if (GWT.isClient() && LogConfiguration.loggingIsEnabled()) {
									GWT.log("Error sending delayed request", ex);
								}
							}
						} else {
							GWT.log("XXXX SCHEDULE 2");
							this.schedule(gracePeriod);
							gracePeriod = gracePeriod * 2;
						}
					} else {
						eventBus.fireEvent(new RuntimeErrorEvent("Authentifizierungsfehler", "Request konnte nicht zum Server gesendet werden, da kein AccessToken zur Verfügung stand.", true));
						Cookies.removeCookie(CookieKeys.JWT_REFRESH_TOKEN);
					}
				}
			};
			GWT.log("XXXX SCHEDULE 1");
			t.schedule(gracePeriod);
			gracePeriod = gracePeriod * 2;

		} else if (response.getStatusCode() == Response.SC_UNAUTHORIZED && accesstokenIsBeiingRefreshed == false) {
			// Token erneuern
			accesstokenIsBeiingRefreshed = true;
			GWT.log("_________> RefreshTokenDispatcherCallback -> onResponseReceived -> Fetiching new Access Token");
			LoginService loginservice = ServiceFactory.getLoginService();
			loginservice.refreshtoken(UserCredentials.INSTANCE.getRefreshToken(), new MethodCallback<JWTToken>() {

				@Override
				public void onSuccess(Method method, JWTToken jwttoken) {
					GWT.log("_________> ForbiddenDispatcherCallback -> onResponseReceived -> Hole neuen JWT -> success");
					UserCredentials.store(jwttoken);
					accesstokenIsBeiingRefreshed = false;
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
					// GWT.log("_________> ForbiddenDispatcherCallback -> onResponseReceived -> Hole
					// neuen JWT -> error");
					accesstokenIsBeiingRefreshed = false;
					eventBus.fireEvent(new RuntimeErrorEvent("Authentifizierungsfehler", "Ihre Session ist abgelaufen. Bitte loggen Sie sich erneut ein.", true));
					Cookies.removeCookie(CookieKeys.JWT_REFRESH_TOKEN);

				}
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