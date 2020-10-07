package com.example.springloomtest;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@RestController
@RequestMapping("/")
public class TestController {
    @RequestMapping(
        method = RequestMethod.GET,
        value = "/status")
    public GetStatusResponse getStatus(HttpServletRequest _request, HttpServletResponse _response) throws Exception {
        FiberHttpServlet.serve(_request, _response, this, "getStatus",
                FiberHttpServlet.getParameterNames(),
                FiberHttpServlet.getParameterArray(),
                null, null, null,
                true);
        return null;
    }

    public GetStatusResponse getStatus() throws Exception {
        GetStatusResponse response = new GetStatusResponse();
        response.setMessage("OK OK NO PROBLEM");
        return response;
    }
}
