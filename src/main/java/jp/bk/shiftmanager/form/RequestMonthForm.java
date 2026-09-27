package jp.bk.shiftmanager.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 申請画面の1か月分の入力（締切前の日だけが送信される） */
@Data
public class RequestMonthForm {
    /** yyyy-MM */
    private String month;
    private List<RequestDayForm> days = new ArrayList<>();
}
