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
    /** 「この期間は出勤できない」にチェックしたサイクルの開始日（yyyy-MM-dd）。締切前のサイクルだけが送信される */
    private List<String> unavailableCycles = new ArrayList<>();
}
