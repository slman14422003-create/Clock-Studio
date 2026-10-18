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
