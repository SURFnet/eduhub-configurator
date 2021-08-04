;; Copyright (C) 2021 SURFnet B.V.
;;
;; This program is free software: you can redistribute it and/or modify it
;; under the terms of the GNU General Public License as published by the Free
;; Software Foundation, either version 3 of the License, or (at your option)
;; any later version.
;;
;; This program is distributed in the hope that it will be useful, but WITHOUT
;; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
;; FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
;; more details.
;;
;; You should have received a copy of the GNU General Public License along
;; with this program. If not, see http://www.gnu.org/licenses/.

(ns ooapi-gateway-configurator.http)

(def ok 200)
(def created 201)
(def accepted 202)
(def non-authoritative-information 203)
(def no-content 204)
(def reset-content 205)
(def partial-content 206)
(def multiple-choices 300)
(def moved-permanently 301)
(def found 302)
(def see-other 303)
(def not-modified 304)
(def use-proxy 305)
(def temporary-redirect 307)
(def bad-request 400)
(def unauthorized 401)
(def payment-required 402)
(def forbidden 403)
(def not-found 404)
(def method-not-allowed 405)
(def not-acceptable 406)
(def proxy-authentication-required 407)
(def request-timeout 408)
(def conflict 409)
(def gone 410)
(def length-required 411)
(def precondition-failed 412)
(def request-entity-too-large 413)
(def request-uri-too-long 414)
(def unsupported-media-type 415)
(def requested-range-not-satisfiable 416)
(def expectation-failed 417)
(def internal-server-error 500)
(def not-implemented 501)
(def bad-gateway 502)
(def service-unavailable 503)
(def gateway-timeout 504)
(def http-version-not-supported 505)
