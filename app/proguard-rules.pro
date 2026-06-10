# Retrofit, OkHttp, kotlinx-serialization, and Koin all ship consumer R8 rules
# in their artifacts, so no library-specific keeps are needed here. Add rules
# only when R8 actually strips something the app needs.
