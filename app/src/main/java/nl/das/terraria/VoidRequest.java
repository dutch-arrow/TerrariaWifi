package nl.das.terraria;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

public class VoidRequest extends Request<Void> {
    private final Response.Listener<Void> listener;
    private final String requestBody;

    public VoidRequest(int method, String url, String requestBody, Response.Listener<Void> listener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.requestBody = requestBody;
        this.listener = listener;
    }
    @Override
    public byte[] getBody() throws AuthFailureError {
        if (requestBody == null) {
            return null;
        } else {
            return requestBody.getBytes();
        }
    }
    @Override
    protected Response<Void> parseNetworkResponse(NetworkResponse response) {
        return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
    }
    @Override
    protected void deliverResponse(Void response) { listener.onResponse(response); }
}
