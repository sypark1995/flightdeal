package com.sypark.flightdeal.feed;

import android.view.View;
import android.widget.TextView;
import androidx.databinding.BindingAdapter;
import com.sypark.flightdeal.R;

/**
 * XML의 {@code app:} 속성에서 직접 참조하는 바인딩 어댑터.
 *
 * 이 프로젝트의 Kotlin/AGP 조합(Kotlin 2.1.0 + AGP/Data Binding 컴파일러 8.7.3)에서는
 * 같은 모듈 안에 Kotlin으로 선언한 {@code @BindingAdapter}가 데이터 바인딩 컴파일러의
 * 클래스패스 스캔으로 전혀 발견되지 않는다(원시 타입 setter조차 "Cannot find a setter"로
 * 실패한다). Java로 선언하면 javac 어노테이션 프로세서가 소스를 직접 읽으므로 이 문제를
 * 피할 수 있다. {@link DealBindingAdapters}의 wonPrice/strikethroughPrice는 별도 이유
 * (Won 값 클래스의 getter 이름 맹글링)로 XML에서 아예 호출할 수 없어 여기 포함하지 않는다.
 */
public final class DealXmlBindingAdapters {

    private DealXmlBindingAdapters() {
    }

    /** 할인 배지. 배지를 달 가치가 없으면 감춘다. */
    @BindingAdapter("discountPercent")
    public static void setDiscountPercent(TextView view, Integer percent) {
        if (percent == null) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        // 화면에는 "평균가 −38%"로 보인다. 음수 부호는 U+2212.
        view.setText(view.getContext().getString(R.string.discount_badge, -percent));
    }

    @BindingAdapter("isVisible")
    public static void setIsVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
