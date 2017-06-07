//
//  ElectrodeBridgeHolderNew.h
//  ElectrodeReactNativeBridge
//
//  Created by Claire Weijie Li on 3/28/17.
//  Copyright © 2017 Walmart. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "ElectrodeBridgeEventNew.h"
#import "ElectrodeBridgeRequestNew.h"

#import "ElectrodeBridgeProtocols.h"

@class ElectrodeBridgeTransceiver;
@protocol ElectrodeBridgeRequestHandler, ElectrodeBridgeEventListener;

NS_ASSUME_NONNULL_BEGIN

/**
 * Client facing class.
 * Facade to ElectrodeBridgeTransceiver.
 * Handles queuing every method calls until react native is ready.
 */

@interface ElectrodeBridgeHolderNew : NSObject

+ (void)sendEvent: (ElectrodeBridgeEventNew *)event;

+ (void)sendRequest: (ElectrodeBridgeRequestNew *)request
  completionHandler: (ElectrodeBridgeResponseCompletionBlock) completion;

+ (void)registerRequestHanlderWithName: (NSString *)name
              requestCompletionHandler: (ElectrodeBridgeRequestCompletionHandler) completion;

+ (void)addEventListnerWithName: (NSString *)name
                   eventListner: (id<ElectrodeBridgeEventListener>) eventListner;

+ (void) setBridge: (ElectrodeBridgeTransceiver *)bridge;
@end
NS_ASSUME_NONNULL_END


