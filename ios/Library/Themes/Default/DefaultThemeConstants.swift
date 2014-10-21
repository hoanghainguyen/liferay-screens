/**
* Copyright (c) 2000-present Liferay, Inc. All rights reserved.
*
* This library is free software; you can redistribute it and/or modify it under
* the terms of the GNU Lesser General Public License as published by the Free
* Software Foundation; either version 2.1 of the License, or (at your option)
* any later version.
*
* This library is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
* FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
* details.
*/
import Foundation


let DefaultThemeButtonCornerRadius:CGFloat = 4

let DefaultThemeBasicBlue = UIColor(red: 0.0, green: 184.0/255.0, blue: 224.0/255.0, alpha: 0.87)


func setDefaultButtonBackground(button: UIButton?) {
	if let buttonImage = UIImage(named: "default-button") {
		let stretchableImage = buttonImage.resizableImageWithCapInsets(
				UIEdgeInsetsMake(5, 5, 5, 5),
				resizingMode: UIImageResizingMode.Stretch)

		button?.setBackgroundImage(stretchableImage, forState: UIControlState.Normal)

		button?.backgroundColor = UIColor.clearColor()
	}
}

public func setAuthMethodStyles(
		#view: UIView,
		#authMethod: AuthMethod,
		#userNameField: UITextField!,
		#userNameIcon: UIImageView!) {

	userNameField!.placeholder = LocalizedString("default", authMethod.description, view)
	userNameField!.keyboardType = authMethod.keyboardType
	userNameIcon?.image = UIImage(named:"default-\(authMethod.iconType)-icon")
}

