package com.cidas.edudoc.ai.api;

import com.cidas.edudoc.ai.application.QaService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-06 控制器：薄委托 + 服务层错误（40001/40401）原样上抛给全局处理器。 */
class QaControllerTest {

    private final QaService qaService = mock(QaService.class);
    private final QaController controller = new QaController(qaService);

    @Test
    void createSessionDelegatesAndWraps() {
        when(qaService.createSession(eq("D1"), eq("t"))).thenReturn(
                new QaService.SessionView("S1", "D1", "t", "A"));
        var response = controller.createSession(new QaController.CreateSessionRequest("D1", "t"));
        assertEquals(200, response.code());
        assertEquals("S1", response.data().sessionId());
    }

    @Test
    void askDelegatesWithQuestionPayload() {
        when(qaService.ask(eq("S1"), eq("问？"))).thenReturn(
                new QaService.AskResult("S1", "QM", "AM", "REFUSED", "拒答", List.of()));

        var response = controller.ask("S1", new QaController.AskRequest("问？"));

        assertEquals("REFUSED", response.data().status());
        verify(qaService).ask("S1", "问？");
    }

    @Test
    void serviceValidationErrorsPropagate() {
        when(qaService.ask(anyString(), anyString())).thenThrow(
                new CidasBusinessException(ErrorCode.BAD_REQUEST, "问题需为 1-1000 字符"));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> controller.ask("S1", new QaController.AskRequest(" ")));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }
}
