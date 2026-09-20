# تطبيق المخابز — Bakery Daily App

مشروع Android أصلي باللغة العربية، يعمل محلياً باستخدام Room، ومجهز للبناء السحابي على Codemagic.

## التقنيات
- Kotlin 2.0.21
- Jetpack Compose / Material 3
- Room 2.6.1
- Navigation Compose
- Android SDK 35
- minSdk 26

## الإصدار الأول
يشمل إدارة المخابز والسجلات اليومية والحسابات والبحث والإحصاءات والوضع الليلي. النسخ الاحتياطي والاستعادة مؤجلان للإصدار اللاحق وفق المتطلبات.

## التشغيل السحابي
يوجد `codemagic.yaml` في جذر المشروع. Workflow التجريبي يبني Debug APK ويشغل الاختبارات. Workflow الإصدار يبني Release APK باستخدام إعداد توقيع Codemagic المسمى `bakery_release_keystore`.

## ملاحظات مهمة
- يجب ألا توضع كلمات مرور Keystore داخل Git.
- يجب اختبار التطبيق على جهاز Android حقيقي قبل اعتماد الإصدار.
- هذا المشروع مصدر Release Ready؛ نجاح APK النهائي يعتمد على تنفيذ Build فعلي في بيئة Android/Codemagic.
