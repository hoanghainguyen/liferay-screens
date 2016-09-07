/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.mobile.screens.base;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.liferay.mobile.screens.R;
import com.liferay.mobile.screens.base.interactor.CustomInteractorListener;
import com.liferay.mobile.screens.base.interactor.Interactor;
import com.liferay.mobile.screens.base.thread.BaseCachedThreadRemoteInteractor;
import com.liferay.mobile.screens.base.thread.BaseThreadInteractor;
import com.liferay.mobile.screens.base.thread.listener.CacheListener;
import com.liferay.mobile.screens.base.view.BaseViewModel;
import com.liferay.mobile.screens.cache.OfflinePolicy;
import com.liferay.mobile.screens.context.LiferayScreensContext;
import com.liferay.mobile.screens.context.LiferayServerContext;
import com.liferay.mobile.screens.context.SessionContext;
import com.liferay.mobile.screens.util.LiferayLocale;
import com.liferay.mobile.screens.util.LiferayLogger;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Silvio Santos
 */
public abstract class BaseScreenlet<V extends BaseViewModel, I extends Interactor> extends FrameLayout
	implements CacheListener {

	public static final String DEFAULT_ACTION = "default_action";
	private static final String STATE_SCREENLET_ID = "basescreenlet-screenletId";
	protected static final String STATE_SUPER = "basescreenlet-super";
	private static final String STATE_INTERACTORS = "basescreenlet-interactors";
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
	private final Map<String, I> interactors = new HashMap<>();
	protected OfflinePolicy offlinePolicy;
	protected long groupId;
	protected long userId;
	protected Locale locale;
	protected CacheListener cacheListener;
	private int screenletId;
	private View screenletView;
	private CustomInteractorListener customInteractorListener;

	public BaseScreenlet(Context context) {
		super(context);

		//		init(context, null);
	}

	public BaseScreenlet(Context context, AttributeSet attrs) {
		super(context, attrs);

		init(context, attrs);
	}

	public BaseScreenlet(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		init(context, attrs);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public BaseScreenlet(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		init(context, attrs);
	}

	private static int generateScreenletId() {

		// This implementation is copied from View.generateViewId() method We
		// cannot rely on that method because it's introduced in API Level 17

		while (true) {
			final int result = NEXT_ID.get();
			int newValue = result + 1;
			if (newValue > 0x00FFFFFF) {
				newValue = 1;
			}
			if (NEXT_ID.compareAndSet(result, newValue)) {
				return result;
			}
		}
	}

	public int getScreenletId() {
		if (screenletId == 0) {
			screenletId = generateScreenletId();
		}

		return screenletId;
	}

	public void performUserAction() {
		I interactor = getInteractor();

		if (interactor != null) {
			getViewModel().showStartOperation(null);
			onUserAction(null, interactor);
		}
	}

	public void performUserAction(String userActionName, Object... args) {
		I interactor = getInteractor(userActionName);

		if (interactor != null) {
			getViewModel().showStartOperation(userActionName);
			onUserAction(userActionName, interactor, args);
		}
	}

	public I getInteractor() {
		return getInteractor(DEFAULT_ACTION);
	}

	public I getInteractor(String actionName) {
		I result = interactors.get(actionName);

		if (result == null) {
			result = prepareInteractor(actionName);
		}

		return result;
	}

	public void setCustomInteractorListener(CustomInteractorListener customInteractorListener) {
		this.customInteractorListener = customInteractorListener;
	}

	protected I prepareInteractor(String actionName) {

		I result = customInteractorListener == null ? createInteractor(actionName)
			: (I) customInteractorListener.createInteractor(actionName);

		if (result != null) {
			if (result instanceof BaseThreadInteractor) {
				BaseThreadInteractor threadInteractor = (BaseThreadInteractor) result;
				threadInteractor.setTargetScreenletId(getScreenletId());
				threadInteractor.setActionName(actionName);

				if (threadInteractor instanceof BaseCachedThreadRemoteInteractor) {
					BaseCachedThreadRemoteInteractor cachedThreadRemoteInteractor =
						(BaseCachedThreadRemoteInteractor) threadInteractor;
					cachedThreadRemoteInteractor.setOfflinePolicy(getOfflinePolicy());
					cachedThreadRemoteInteractor.setGroupId(getGroupId());
					cachedThreadRemoteInteractor.setUserId(getUserId());
					cachedThreadRemoteInteractor.setLocale(getLocale());
				}
			}

			result.onScreenletAttached(this);
			interactors.put(actionName, result);
		}
		return result;
	}

	protected void init(Context context, AttributeSet attributes) {
		LiferayScreensContext.init(context);

		TypedArray typedArray =
			context.getTheme().obtainStyledAttributes(attributes, R.styleable.OfflineScreenlet, 0, 0);

		groupId = castToLongOrUseDefault(typedArray.getString(R.styleable.OfflineScreenlet_groupId),
			LiferayServerContext.getGroupId());

		Long userAttribute = castToLong(typedArray.getString(R.styleable.OfflineScreenlet_userId));
		Long userId = SessionContext.getUserId();
		this.userId = (userAttribute == 0 ? (userId == null ? 0 : userId) : userAttribute);

		String localeAttribute = typedArray.getString(R.styleable.OfflineScreenlet_locale);
		locale = locale == null ? LiferayLocale.getDefaultLocale() : new Locale(localeAttribute);

		Integer offlinePolicyAttribute =
			typedArray.getInteger(R.styleable.OfflineScreenlet_offlinePolicy, OfflinePolicy.REMOTE_ONLY.ordinal());
		offlinePolicy = OfflinePolicy.values()[offlinePolicyAttribute];

		assignView(createScreenletView(context, attributes));
	}

	public void render(int layoutId) {
		LiferayScreensContext.init(getContext());

		assignView(LayoutInflater.from(getContext()).inflate(layoutId, null));
	}

	protected void assignView(View view) {
		if (!isInEditMode()) {
			screenletView = view;

			getViewModel().setScreenlet(this);

			addView(screenletView);
		}
	}

	protected V getViewModel() {
		return (V) screenletView;
	}

	protected int getDefaultLayoutId() {
		try {
			Context ctx = getContext().getApplicationContext();
			String packageName = ctx.getPackageName();

			// first, get the identifier of the string key
			String layoutNameKeyName = getClass().getSimpleName() + "_" + getLayoutTheme();
			int layoutNameKeyId = ctx.getResources().getIdentifier(layoutNameKeyName, "string", packageName);

			if (layoutNameKeyId == 0) {
				layoutNameKeyId =
					ctx.getResources().getIdentifier(getClass().getSimpleName() + "_default", "string", packageName);
			}

			// second, get the identifier of the layout specified in key layoutNameKeyId

			String layoutName = ctx.getString(layoutNameKeyId);
			return ctx.getResources().getIdentifier(layoutName, "layout", packageName);
		} catch (Exception e) {
			//We don't want to crash if the user creates a custom screenlet without adding a
			// default layout to it
			return 0;
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if (!isInEditMode()) {
			for (I interactor : interactors.values()) {
				interactor.onScreenletAttached(this);
			}
		}

		onScreenletAttached();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		onScreenletDetached();

		if (!isInEditMode()) {
			for (I interactor : interactors.values()) {
				interactor.onScreenletDetached(this);
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(Parcelable inState) {
		Bundle state = (Bundle) inState;
		Parcelable superState = state.getParcelable(STATE_SUPER);

		super.onRestoreInstanceState(superState);

		// The screenletId is restored only if it was not generated yet. If the
		// screenletId already exists at this point, it means that an interactor
		// is using it, so we cannot restore the previous value. As a side
		// effect, any previous executing task will not deliver the result to
		// the new interactor. To avoid this behavior, only call screenlet
		// methods after onStart() activity/fragment callback. This ensures that
		// onRestoreInstanceState was already called.
		// TODO: Create restore method?

		if (screenletId == 0) {
			screenletId = state.getInt(STATE_SCREENLET_ID);
		}

		String[] stateInteractors = state.getStringArray(STATE_INTERACTORS);
		if (stateInteractors != null) {
			for (String actionName : stateInteractors) {
				prepareInteractor(actionName);
			}
		}
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable(STATE_SUPER, superState);
		state.putInt(STATE_SCREENLET_ID, screenletId);
		state.putStringArray(STATE_INTERACTORS, interactors.keySet().toArray(new String[interactors.size()]));

		return state;
	}

	protected void onScreenletAttached() {
	}

	protected void onScreenletDetached() {
	}

	protected abstract View createScreenletView(Context context, AttributeSet attributes);

	protected abstract I createInteractor(String actionName);

	protected abstract void onUserAction(String userActionName, I interactor, Object... args);

	protected long castToLong(String value) {
		return castToLongOrUseDefault(value, 0);
	}

	protected long castToLongOrUseDefault(String value, long defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			LiferayLogger.e("You have supplied a string and we expected a long number", e);
			throw e;
		}
	}

	@NonNull
	protected String getLayoutTheme() {
		String result = applyTheme(getActivityTheme());

		if (result == null) {
			result = applyTheme(getApplicationTheme());
		}
		return result == null ? "default" : result;
	}

	private String getActivityTheme() {
		try {
			TypedValue outValue = new TypedValue();
			LiferayScreensContext.getActivityFromContext(getContext())
				.getTheme()
				.resolveAttribute(R.attr.themeName, outValue, true);
			return (String) outValue.string;
		} catch (Exception e) {
			LiferayLogger.d("Screens theme not found");
		}
		return null;
	}

	private String getApplicationTheme() {
		try {
			Context ctx = getContext().getApplicationContext();
			String packageName = ctx.getPackageName();
			PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
			int applicationThemeId = packageInfo.applicationInfo.theme;
			return getResources().getResourceEntryName(applicationThemeId);
		} catch (Exception e) {
			LiferayLogger.d("Screens theme not found");
		}
		return null;
	}

	private String applyTheme(String themeName) {
		if (themeName != null && themeName.contains("_theme")) {
			return themeName.substring(0, themeName.indexOf("_theme"));
		}
		return null;
	}

	@Override
	public void loadingFromCache(boolean success) {
		if (cacheListener != null) {
			cacheListener.loadingFromCache(success);
		}
	}

	@Override
	public void retrievingOnline(boolean triedInCache, Exception e) {
		if (cacheListener != null) {
			cacheListener.retrievingOnline(triedInCache, e);
		}
	}

	@Override
	public void storingToCache(Object object) {
		if (cacheListener != null) {
			cacheListener.storingToCache(object);
		}
	}

	public long getGroupId() {
		return groupId;
	}

	public void setGroupId(long groupId) {
		this.groupId = groupId;
	}

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	public OfflinePolicy getOfflinePolicy() {
		return offlinePolicy;
	}

	public void setOfflinePolicy(OfflinePolicy offlinePolicy) {
		this.offlinePolicy = offlinePolicy;
	}

	public CacheListener getCacheListener() {
		return cacheListener;
	}

	public void setCacheListener(CacheListener cacheListener) {
		this.cacheListener = cacheListener;
	}
}