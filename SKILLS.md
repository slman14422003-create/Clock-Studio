# SKILLS — ملخص المهمة الأخيرة (للمتابعة)

## المهمة: واجهة القرآن + التفسير + إصلاح ويدجتس الصلاة + تنسيق أزرار التخصيص

### السبب الجذري للأخطاء
- ويدجت "أوقات الصلاة" و"أرقام كبيرة" كانا يفشلان ("Couldn't add widget" / "تعذّرت المعاينة") لأن التخطيطات تحتوي عنصر `<View>` عادي وهو غير مسموح داخل RemoteViews. استُبدل بـ `FrameLayout` في: wl_prayers_ltr/rtl و wl_digital_ltr/rtl.
- زرا "افتراضي/حفظ" غير منسقين لأن `Ui.text()` كان يفرض `TEXT_ALIGNMENT_VIEW_START` فيتجاهل `setGravity(CENTER)`. أُزيل السطر من Ui.text، وأُضيف توسيط صريح وحشوة للزرين في WidgetConfigActivity.

### قاعدة للمستقبل
- في تخطيطات الويدجت (res/layout/wl_*.xml) المسموح فقط: FrameLayout, LinearLayout, RelativeLayout, GridLayout, TextView, Button, ImageView, ImageButton, ProgressBar, Chronometer, TextClock, AnalogClock, ViewFlipper, ListView, GridView, StackView. ممنوع: View, Space, ScrollView, EditText.
- أي TextView يُستخدم معه setGravity للتوسيط يجب ألا يُفرض عليه TEXT_ALIGNMENT_VIEW_START.

### ما أُضيف في القرآن
- QuranTafsir.java: جلب التفسير من api.alquran.cloud (الميسر، الجلالين، القرطبي، البغوي، الوسيط، تنوير المقباس، English) مع تخزين محلي في files/tafsir/ ليعمل دون إنترنت بعد أول فتح.
- QuranTafsirActivity.java: شاشة الآية + اختيار التفسير + السابقة/التالية + نسخ ومشاركة. تُفتح من: بطاقة آية اليوم، نتائج البحث، أيقونة (i) في شريط تحديد الآية بالمصحف.
- QuranData.searchAyahs / locate: بحث في نص كل الآيات بدون تشكيل، ويدعم المرجع مثل 2:255 أو "البقرة 255".
- QuranActivity: بطاقة آية اليوم (اقرأ/تفسير)، أقسام نتائج (السور + الآيات)، صفوف آيات بخط المصحف.
- Manifest: تسجيل QuranTafsirActivity. strings: مفاتيح quran_tafsir و tafsir_names وغيرها (عربي + إنجليزي).

### لم يُختبر بعد (يحتاج بناء وتجربة على الجهاز)
- لم يُشغَّل البناء في بيئة العمل (لا يوجد Android SDK هنا)؛ شغّل build.yml وتأكد من الترجمة.
- أسماء إصدارات التفسير (ar.waseet, ar.miqbas, ar.qurtubi, ar.baghawi) تعتمد على توفرها في alquran.cloud؛ إن فشل أحدها يظهر "تعذّر التحميل" ويمكن حذفه من QuranTafsir.IDS و tafsir_names.
- قاموس معاني المفردات لكل كلمة غير مضاف؛ المضاف هو التفسير الكامل للآية.

---

## المهمة التالية: تتبع الآية أثناء التلاوة + شريط تشغيل بموجة
- سبب شكل التظليل الأزرق القبيح: كان BackgroundColorSpan يملأ صندوق السطر كاملاً (مع تباعد الأسطر 1.6). استُبدل بـ AyahTextView الذي يرسم مستطيلاً مدوّراً شفافاً حول الكلمات فقط (باستخدام Layout.getPrimaryHorizontal وقياسات الخط).
- WaveView.java: موجة للآية الجارية مع فقاعة تعرض رقم الآية فوق مؤشر التقدّم، وتُسحب للانتقال داخل الآية. الاتجاه من اليمين لليسار.
- شريط التشغيل (playerPill) في QuranReaderActivity بين الصفحات وشريط الصفحات: السابقة، تشغيل/إيقاف، التالية + الموجة. يظهر عند التشغيل أو الإيقاف المؤقت ويختفي عند الإيقاف.
- QuranPlayer: أُضيفت current/position/duration/seekFraction/next/previous.
- أيقونات جديدة: ic_ayah_prev, ic_ayah_next (للتخطيط من اليمين لليسار: السابقة تشير لليمين).
- لم يُبنَ المشروع هنا؛ جرّب على الجهاز: دقة موضع التظليل مع الخط المخصص quran.ttf والمحاذاة المضبوطة (JUSTIFICATION_MODE_INTER_WORD).

---

## المهمة التالية: إصلاح معاينة التخصيص + إعادة تصميم كل الويدجتس
- سبب "تعذّرت المعاينة": WidgetConfigActivity كان يستدعي `rv.apply(this, holder)` بسياق الأكتيفيتي، فيحقن AppCompat مكوّنات (AppCompatTextView...) غير مسموحة في RemoteViews. الحل: السياق `ContextThemeWrapper(applicationContext, AppTheme)`.
- قاعدة: أي معاينة RemoteViews داخل التطبيق تُطبَّق بسياق التطبيق وليس الأكتيفيتي.
- التخطيطات الثمانية (classic, digital, stack, date, alarm, prayer, prayers, quran) × (ltr, rtl) تُولَّد كلها من سكربت واحد، وكل المعرفات (wP1, wA1, ...) محفوظة كما هي. لتعديل التصميم عدّل السكربت ثم أعد توليد الملفات بدل التعديل اليدوي على 16 ملفاً.
- عناصر جديدة: wb_line (فاصل)، wb_circle (دائرة خلف الأيقونة)، wb_cell_on/off (تمييز الصلاة القادمة بخلفية خلية في ويدجت أوقات الصلاة عبر setBackgroundResource).
- ويدجت الصلاة القادمة: حجم نص العدّاد صار 26 (حدّثت sizes() في WidgetPrayer).
- الساعة التناظرية: وجوه a/w/b أعيد توليدها بـ 60 علامة (الساعات أسمك) وعقارب أنعم وغطاء مركزي.
- لم يُبنَ المشروع هنا؛ تحقق على الجهاز من معاينة كل ويدجت ومن أحجام الخطوط عند 70% و150%.
- مكان السكربت: gen_widgets.py.txt في جذر المشروع (غيّر الامتداد إلى .py وشغّله من app/src/main/res).

---

## المهمة التالية: فقاعة زجاجية + بطاقة واتساب + StandBy فوق القفل + ويدجتس بأحجام أكثر

### 1) تحديد الآية (فقاعة زجاجية)
- AyahTextView يرسم لكل سطر فقاعة: توهج ناعم، تعبئة متدرجة شفافة، حد متدرج أبيض، ولمعة علوية داخل الفقاعة. اللون يؤخذ من RGB للون التمييز، والشفافية داخلية في الكلاس (تمرير Ui.alpha(accent, 0xFF) من QuranReaderActivity).

### 2) مشاركة الآية كبطاقة / حالة واتساب
- ShareCard.java: يرسم صورة 1080×1920 (مقاس الحالة) بخلفية متدرجة وبطاقة زجاجية، اسم السورة في كبسولة، الآية بخط المصحف بحجم يتقلّص تلقائياً (وإن طالت جداً تُقتطع بنقاط)، والمرجع والعلامة أسفلها. 5 ثيمات تُحفظ في Store.shareTheme.
- زر المشاركة في القارئ وفي شاشة التفسير يفتح معاينة مع ثلاثة خيارات: حالة واتساب (setPackage على com.whatsapp ثم w4b، وإلا نافذة المشاركة العامة)، صورة، نص.
- FileProvider: authority = <packageName>.fileprovider، المسار cache/share (res/xml/file_paths.xml)، وqueries لحزمتي واتساب في المانيفست.

### 3) وضع الاستعداد (StandBy)
- صفحات: رقمي، تناظري، ليلي، الصلاة القادمة (عدّاد + 5 أوقات)، آية اليوم، تقويم الشهر. سطر التاريخ الهجري يظهر في الرقمي إذا كان موقع الصلاة مضبوطاً. الضغط المطوّل يبدّل لون الساعة (6 ألوان)، نقاط الصفحات أسفل الشاشة، وتلميح يظهر أول مرة فقط.
- ليلي تلقائي: حسّاس الإضاءة (أقل من 3 لوكس يفعّل الأحمر الخافت، وفوق 12 يعود).
- فوق شاشة القفل: setShowWhenLocked بحسب إعداد standby_lock (لا يوجد showWhenLocked ثابت في المانيفست عمداً).
- التشغيل التلقائي: StandByService (خدمة أمامية specialUse) تسجّل ديناميكياً POWER_CONNECTED/DISCONNECTED/SCREEN_ON وتراقب الاتجاه؛ عند الشحن + الشاشة تعمل + الهاتف مقفل (+ أفقي إن فُعّل) تفتح StandByActivity عبر إشعار fullScreenIntent (القناة standby_launch) وبالتحديد عبر startActivity إذا كان إذن العرض فوق التطبيقات ممنوحاً. عند فصل الشاحن تُغلق الشاشة المفتوحة تلقائياً (closeAuto). BootReceiver يعيد تشغيل الخدمة بعد الإقلاع/التحديث.
- الأذونات الجديدة: FOREGROUND_SERVICE_SPECIAL_USE، SYSTEM_ALERT_WINDOW (+ USE_FULL_SCREEN_INTENT موجود). صفوف الأذونات في الإعدادات تشمل الآن العرض فوق التطبيقات.
- أندرويد 16: targetSdk 36 أصلاً؛ الواجهة كاملة الشاشة بحافة لحافة (setDecorFitsSystemWindows false وlayoutInDisplayCutoutMode)، وكل بدء للخدمة الأمامية داخل try/catch.

### 4) الويدجتس
- كل ملفات widget_*_info.xml صار فيها minResizeWidth/Height = 40dp وmaxResize = 700dp (أندرويد 12+).
- WBase.measure: يحسب معامل التكبير من حجم الويدجت الفعلي ويضرب به أحجام الخطوط في sizes() (بين 0.55 و2.6)، فيكبر النص ويصغر مع سحب الويدجت. WidgetDate وWidgetStack أُضيف لهما sizes().
- 9 نسخ كبيرة (ClockWidgetL, WidgetAnalogL, WidgetDigitalL, WidgetDateL, WidgetQuranL, WidgetPrayerL, WidgetStackL, WidgetPrayersL, WidgetAlarmL) تظهر في قائمة الويدجتس بأحجام افتراضية أكبر (4×2 إلى 4×4)، وتقيس تكبيرها نسبةً إلى تصميم النسخة الأصلية (قاعدة: الصنف المنتهي بحرف L نسخته الأساسية بدونه). عند إضافة ويدجت جديد أضف نسخته L في WBase.ALL والمانيفست وxml.

### لم يُختبر بعد
- لا يوجد Android SDK في بيئة العمل، فلم يُبنَ المشروع. شغّل build.yml وجرّب: معاينة بطاقة المشاركة وإرسالها لحالة واتساب، ظهور StandBy فوق القفل عند الشحن (يحتاج منح إذن الشاشة الكاملة)، وتقدير حجم الخط في الويدجتس بعد السحب على الهاتف.

### إصلاح: إزاحة فقاعة التحديد عن الكلمات
- السبب: النص يُضبط بـ JUSTIFICATION_MODE_INTER_WORD، و Layout.getPrimaryHorizontal يعيد المواضع قبل التوزيع، فتنزاح الفقاعة عن الكلمات. الحل: AyahTextView.xAt يضيف الفراغ الموزّع (عرض الطبقة − getLineMax)/عدد المسافات لكل مسافة تسبق الموضع (باتجاه RTL)، ولا يُطبَّق على السطر الأخير.
