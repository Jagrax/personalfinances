package ar.com.personalfinances.api.galicia;

import ar.com.personalfinances.api.galicia.client.GaliciaApiConnector;
import ar.com.personalfinances.api.galicia.io.GetMovimientosCuentaResponse;
import ar.com.personalfinances.api.galicia.model.Model;
import ar.com.personalfinances.util.CommonResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Slf4j
public class GaliciaApiManagerBean implements GaliciaApiManager {

    public CommonResult getMovimientosCuenta(Date from, Date to) {
        return getMovimientosCuenta(from, to, 0L);
    }
    private CommonResult getMovimientosCuenta(Date from, Date to, Long pageNumber) {
        GaliciaApiConnector galiciaApiConnector = new GaliciaApiConnector("https://cuentas.bancogalicia.com.ar");
        GetMovimientosCuentaResponse getMovimientosCuentaResponse = galiciaApiConnector.getMovimientosCuenta(
                "R2D2=https://bcdn-god.we-stats.com/scripts/ad1a29c5/ad1a29c5.js; TS017bfb32=01f07bd103831ac75000af55355325fe513357e04086fc471ac47262ab95d7e9e26a2c89ec2ec8aa0431b81293eb540245181e53cb; at_check=true; cdContextId=4965; 51630000_clogin=v=37&l=1727048892&e=1727050692278; ASP.NET_SessionId=kajpxc34lw35wf4nhbkf32ch; ExternalDataReference=c90f549fc77998c4014fa5592f562a4a5779c3be23e54a4d83293003d36c8966; Luke=e7a34250-b25c-4363-a891-a184c640c032; Leia=d5372330-4b58-4dd4-adea-6f345fe8fb5d; session_id=d5372330-4b58-4dd4-adea-6f345fe8fb5d; PMdata=PMV68XlKr9ZTKRa3mp9Y5PGuyuRSaiCG7KJtR2DffHbpHT805vcX8LyV7Jd3jOJDu2IQhaPLDEcAHMBmdaTk%2BJhRY6eg%3D%3D; Skywalker=eyJhbGciOiJSUzI1NiJ9.eyJqdGkiOiJjZTE4MzcwYi1mMTc3LTQzYTMtOTdhZC0wYzczODY4MzliODYiLCJpYXQiOjE3NDMzOTkxNzcsImlzcyI6IlBPQ0EiLCJzdWIiOiI4NTUwMzQiLCJhdWQiOiJvbmxpbmViYW5raW5nIiwiYnJhbmNoLWlkIjpudWxsLCJjYXAtc2NvcGUiOiJpZGVudGl0eSIsImNhc2hib3gtaWQiOm51bGwsImNsaWVudC1hdHRlbnRpb24taWQiOm51bGwsImNsaWVudC1kZXZpY2UtaWQiOm51bGwsImNsaWVudC1pZCI6Ik9CQVciLCJjbGllbnQtaXAiOm51bGwsImNsaWVudC1zZXNzaW9uLWlkIjpudWxsLCJkZXZpY2UtcHJpbnQiOm51bGwsImVtcGxveWVlLWlkIjpudWxsLCJlbXBsb3llZS1zdXBlcnZpc29yLWlkIjpudWxsLCJmdW5jaW9uYWxpdHktaWQiOiJjYXBfaW1wZXJzb25hbF9sb2dpbl92NF9sb2dpbiIsImdvLWlkIjpudWxsLCJnby1pZC1udW0iOm51bGwsImlkLWFkaGVzaW9uIjoiNTQ1Mjc4NSIsImlkLWhvc3QiOiIxNjEwMzMyNCIsImlkLXBlcnNvbmEtcG9tIjoiODU1MDM0IiwiaWQtdXN1YXJpby1nbyI6bnVsbCwiaWRfY2hhbm5lbCI6Im9ubGluZWJhbmtpbmciLCJpZF9wb21fb3duZXIiOiI4NTUwMzQiLCJpZF9wb21fdXNlciI6Ijg1NTAzNCIsImlzLWN1c3RvbWVyIjp0cnVlLCJpcy1lbXBsb3llZSI6ZmFsc2UsImp3dC12ZXJzaW9uIjoiMy4wLjAiLCJvd25lci1kb2N1bWVudC1pZCI6IjM5ODAwNzcyIiwib3duZXItZG9jdW1lbnQtdHlwZSI6IkRVIiwib3duZXItcGVyc29uLXR5cGUiOiJGIiwicGVyc29uLXR5cGUiOiJGIiwic2VnbWVudC1jb2RlIjoiMjExMDAzMCIsInNlZ21lbnQtaWQiOiJFTUlORU5UIiwic2ltdWxhdGlvbi1tb2RlIjpmYWxzZSwic2luZ2xlLXVzZXItbWFyayI6InRydWUiLCJ0ZXJtaW5hbC1pZCI6bnVsbCwidXNlci1kYXRhIjp7ImJpcnRoLWRhdGUiOiIxOS0wOS0xOTk2IiwiYnVzaW5lc3MtbW9kZWwiOiJleHByZXNzIiwiZmlyc3QtbmFtZSI6IkpBVklFUiBBTEVKQU5EUk8iLCJmaXJzdC1uYW1lLW93bmVyIjoiSkFWSUVSIEFMRUpBTkRSTyIsImdlbmRlciI6Ik0iLCJpZC1hZGhlc2lvbiI6IjU0NTI3ODUiLCJpZC1jbGllbnQiOiJPQkFXIiwiaWQtaG9zdCI6IjE2MTAzMzI0IiwiaWQtcGVyc29uYS1wb20iOiI4NTUwMzQiLCJpZF9wb21fb3duZXIiOiI4NTUwMzQiLCJpZF9wb21fcmVsYXRpb24iOiIxMzM3NzM5IiwiaWRfcG9tX3VzZXIiOiI4NTUwMzQiLCJsYXN0LW5hbWUiOiJHSUFOSSIsImxhc3QtbmFtZS1vd25lciI6IkdJQU5JIiwibG9naW4tdHJhY2tpbmctaWQiOiJhZTA0MWQwOS0yNmIxLTQyOTAtYTM2OS1kY2UwOTYwM2I2OWIiLCJwb20tb3duZXItZG9jdW1lbnRzIjp7IkRVIjoiMzk4MDA3NzIiLCJDVUlMIjoiMjAzOTgwMDc3MjgifSwicG9tX3VzZXJfZG9jdW1lbnRzIjp7IkRVIjoiMzk4MDA3NzIiLCJDVUlMIjoiMjAzOTgwMDc3MjgifSwicmVxdWlyZXNfZGlnaXRhbF9zaWduYXR1cmUiOiJmYWxzZSJ9LCJ1c2VyLWRvY3VtZW50LWlkIjoiMzk4MDA3NzIiLCJ1c2VyLWRvY3VtZW50LXR5cGUiOiJEVSIsInVzZXItaWQiOm51bGwsImV4cCI6MTc0MzM5OTQ3N30.fORJySXF8Rwo7tdJq9yGwYG6NXcW-yRFDmllMtuUBLQ2Xu1Hf3q9c51nAKO3oSy29Dc6FpZ662eUrVUMLjt9OpeCUAOuDrHKyfvsWNeL51Dvsbks58Xq16KgyzXHyx-Mo1aU3d3eIwF6TEhlBZxq4wHKzPYZEoFtawKRzkkMGsbz6BZTN_EKyTMBWB5in9Yfr0Q7MXqGkuIfPE_OEe_5BT-8FPN4BjxingQqKi9yXy43tyO2UdtXZsMMuXN8CBeUdQ3kQMzpuxYwmvPsyMbDGdZ-yx63NalfT-hZK33RRBVSa5n-9XmesvwWM_j5EBDyKo23jrmlnuSXnP6u3zreSw; SameSite=None; TS010dd3b2=01f07bd1039827e7422c56c33674b12f6a72898b3a8b0db321eadf1e4617c83cbe296e599879cece89b37e881f37d73f8ef23be311",
                from, to, GaliciaApiConnector.TipoMovimiento.TODOS, pageNumber
        );

        if (getMovimientosCuentaResponse == null) {
            return CommonResult.error("getMovimientosCuenta returns null");
        }

        if (getMovimientosCuentaResponse.getError()) {
            return CommonResult.error("getMovimientosCuenta returns error");
        } else {
            Model model = getMovimientosCuentaResponse.getModel();
            if (model.getTotalPaginas() != null && model.getTotalPaginas() > 0) {
                Long actualPage = model.getNumeroPagina();
                if (!actualPage.equals(model.getTotalPaginas())) {
                    // TODO: Ver de paginar sobre el resultado
                }
                return CommonResult.ok(model.getMovimientos());
            } else {
                return CommonResult.ok(model.getMovimientos());
            }
        }
    }
}