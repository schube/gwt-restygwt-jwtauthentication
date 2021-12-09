package com.schubec.myabby.client.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.RestService;

import com.schubec.myabby.client.apimodels.Anrufstatus;
import com.schubec.myabby.client.apimodels.responses.ModelResponse;

public interface AnrufstatusService extends RestService, BaseService {
	@GET
	@Path(BASEURL + "/anrufstati")
	public void getAnrufstati(MethodCallback<ModelResponse<Anrufstatus>> callback);
}