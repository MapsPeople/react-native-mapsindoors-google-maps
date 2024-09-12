import Foundation
import React

import MapsIndoorsCore
import MapsIndoorsGoogleMaps
import GoogleMaps

@objc(MapsIndoorsViewManager)
class MapsIndoorsViewManager : RCTViewManager {
    @objc override static func requiresMainQueueSetup() -> Bool {return false}

    override func view() -> UIView! {
        let aalborgCam = GMSCameraPosition(latitude: 57.04, longitude: 9.9217, zoom: 11.25)
        let gmsMapView = GMSMapView(frame: .zero, camera: aalborgCam)

        let googleMapsView = GoogleMapsView(gmsMapView: gmsMapView)
        MapsIndoorsData.reset()
        MapsIndoorsData.sharedInstance.mapView = googleMapsView
        return gmsMapView
    }

    @objc func create(_ node: NSNumber, nodeAgain: NSNumber) {
    }
}

class GoogleMapsView: RCMapView {
    func animateCamera(cameraUpdate: CameraUpdate, duration: Int) throws {
        Task {@MainActor in
            let gmsCameraUpdate: GMSCameraUpdate
            
            gmsCameraUpdate = try makeGMSCameraUpdate(cameraUpdate: cameraUpdate)
            
            // TODO: duration is not actually being used
            if (duration != 0) {
                googleMapsView.animate(with: gmsCameraUpdate)
            } else {
                googleMapsView.moveCamera(gmsCameraUpdate)
            }
        }
    }
    
    func moveCamera(cameraUpdate: CameraUpdate) throws {
        Task {@MainActor in
            let gmsCameraUpdate: GMSCameraUpdate
            gmsCameraUpdate = try makeGMSCameraUpdate(cameraUpdate: cameraUpdate)

            googleMapsView.moveCamera(gmsCameraUpdate)
        }
    }
    
    func getConfig(config: NSDictionary) -> MPMapConfig {
        return MPMapConfig(gmsMapView: googleMapsView, googleApiKey: "")
    }
    
    private let googleMapsView: GMSMapView
    private var mMapControl: MPMapControl? = nil

    init(gmsMapView: GMSMapView) {
        googleMapsView = gmsMapView
    }
    
    internal func getMapControl() -> MPMapControl? {
        return mMapControl
    }
    
    func setMapControl(mapControl: any MapsIndoors.MPMapControl) {
        mMapControl = mapControl
    }

    private func makeGMSCameraUpdate(cameraUpdate: CameraUpdate) throws -> GMSCameraUpdate {
        let update: GMSCameraUpdate

        switch cameraUpdate.mode {
        case "fromPoint":
            guard let point = cameraUpdate.point else {
                throw CameraUpdateError.missingField("fromPoint", "point")
            }
            update = GMSCameraUpdate.setTarget(point.coordinate)
        case "fromBounds":
            guard let bounds = cameraUpdate.bounds else {
                throw CameraUpdateError.missingField("fromBounds", "bounds")
            }
            update = GMSCameraUpdate.fit(GMSCoordinateBounds(coordinate: bounds.northEast, coordinate: bounds.southWest), withPadding: CGFloat(integerLiteral: cameraUpdate.padding!))
        case "zoomBy":
            update = (GMSCameraUpdate.zoom(by: cameraUpdate.zoom!))
        case "zoomTo":
            update = (GMSCameraUpdate.zoom(to: cameraUpdate.zoom!))
        case "fromCameraPosition":
            guard let position = cameraUpdate.position else {
                throw CameraUpdateError.missingField("fromCameraPosition", "position")
            }
            update = GMSCameraUpdate.setCamera(
                GMSCameraPosition(latitude: CLLocationDegrees(position.target.latitude),
                longitude: CLLocationDegrees(position.target.longitude),
                zoom: position.zoom,
                bearing: CLLocationDirection(floatLiteral: Double(position.bearing)),
                viewingAngle: Double(position.tilt))
                )
        default:
            throw CameraUpdateError.unknownMode(cameraUpdate.mode)
        }

        return update
    }

}
