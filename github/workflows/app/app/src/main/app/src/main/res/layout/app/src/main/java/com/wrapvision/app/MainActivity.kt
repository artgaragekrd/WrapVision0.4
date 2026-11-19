<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#101820"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="WrapVision"
        android:textColor="#FFFFFF"
        android:textSize="28sp"
        android:textStyle="bold"
        android:gravity="center"
        android:padding="12dp" />

    <TextView
        android:id="@+id/statusText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Студийный режим"
        android:textColor="#FFFFFF"
        android:textSize="16sp"
        android:gravity="center"
        android:padding="8dp" />

    <androidx.camera.view.PreviewView
        android:id="@+id/cameraPreview"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <Button
        android:id="@+id/selectColorButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Выбрать цвет плёнки" />

    <Button
        android:id="@+id/bodyModeButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Весь кузов" />

    <Button
        android:id="@+id/partsModeButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Отдельные детали" />

</LinearLayout>
