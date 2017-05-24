webpackJsonp([1,4],{

/***/ 141:
/***/ (function(module, exports) {

function webpackEmptyContext(req) {
	throw new Error("Cannot find module '" + req + "'.");
}
webpackEmptyContext.keys = function() { return []; };
webpackEmptyContext.resolve = webpackEmptyContext;
module.exports = webpackEmptyContext;
webpackEmptyContext.id = 141;


/***/ }),

/***/ 142:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
__webpack_require__(162);
var platform_browser_dynamic_1 = __webpack_require__(157);
var core_1 = __webpack_require__(10);
var environment_1 = __webpack_require__(161);
var _1 = __webpack_require__(160);
if (environment_1.environment.production) {
    core_1.enableProdMode();
}
platform_browser_dynamic_1.platformBrowserDynamic().bootstrapModule(_1.AppModule);
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/main.js.map

/***/ }),

/***/ 158:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
var platform_browser_1 = __webpack_require__(33);
var core_1 = __webpack_require__(10);
var forms_1 = __webpack_require__(88);
var http_1 = __webpack_require__(156);
var clarity_angular_1 = __webpack_require__(90);
var app_component_1 = __webpack_require__(89);
var utils_module_1 = __webpack_require__(165);
var app_routing_1 = __webpack_require__(159);
var AppModule = (function () {
    function AppModule() {
    }
    return AppModule;
}());
AppModule = __decorate([
    core_1.NgModule({
        declarations: [
            app_component_1.AppComponent
        ],
        imports: [
            platform_browser_1.BrowserModule,
            forms_1.FormsModule,
            http_1.HttpModule,
            clarity_angular_1.ClarityModule.forRoot(),
            utils_module_1.UtilsModule,
            app_routing_1.ROUTING
        ],
        providers: [],
        bootstrap: [app_component_1.AppComponent]
    })
], AppModule);
exports.AppModule = AppModule;
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/app/app.module.js.map

/***/ }),

/***/ 159:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var router_1 = __webpack_require__(47);
exports.ROUTES = [
    { path: '', redirectTo: 'home', pathMatch: 'full' }
];
exports.ROUTING = router_1.RouterModule.forRoot(exports.ROUTES);
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/app/app.routing.js.map

/***/ }),

/***/ 160:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
Object.defineProperty(exports, "__esModule", { value: true });
__export(__webpack_require__(89));
__export(__webpack_require__(158));
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/app/index.js.map

/***/ }),

/***/ 161:
/***/ (function(module, exports, __webpack_require__) {

"use strict";
// The file contents for the current environment will overwrite these during build.
// The build system defaults to the dev environment which uses `environment.ts`, but if you do
// `ng build --env=prod` then `environment.prod.ts` will be used instead.
// The list of which env maps to which file can be found in `angular-cli.json`.

Object.defineProperty(exports, "__esModule", { value: true });
exports.environment = {
    production: true
};
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/environments/environment.js.map

/***/ }),

/***/ 162:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
// This file includes polyfills needed by Angular 2 and is loaded before
// the app. You can add your own extra polyfills to this file.
__webpack_require__(179);
__webpack_require__(172);
__webpack_require__(168);
__webpack_require__(174);
__webpack_require__(173);
__webpack_require__(171);
__webpack_require__(170);
__webpack_require__(178);
__webpack_require__(167);
__webpack_require__(166);
__webpack_require__(176);
__webpack_require__(169);
__webpack_require__(177);
__webpack_require__(175);
__webpack_require__(180);
__webpack_require__(358);
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/polyfills.js.map

/***/ }),

/***/ 163:
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/*
 * Hack while waiting for https://github.com/angular/angular/issues/6595 to be fixed.
 */

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(10);
var router_1 = __webpack_require__(47);
var HashListener = (function () {
    function HashListener(route) {
        var _this = this;
        this.route = route;
        this.sub = this.route.fragment.subscribe(function (f) {
            _this.scrollToAnchor(f, false);
        });
    }
    HashListener.prototype.ngOnInit = function () {
        this.scrollToAnchor(this.route.snapshot.fragment, false);
    };
    HashListener.prototype.scrollToAnchor = function (hash, smooth) {
        if (smooth === void 0) { smooth = true; }
        if (hash) {
            var element = document.querySelector("#" + hash);
            if (element) {
                element.scrollIntoView({
                    behavior: smooth ? "smooth" : "instant",
                    block: "start"
                });
            }
        }
    };
    HashListener.prototype.ngOnDestroy = function () {
        this.sub.unsubscribe();
    };
    return HashListener;
}());
HashListener = __decorate([
    core_1.Directive({
        selector: "[hash-listener]",
        host: {
            "[style.position]": "'relative'"
        }
    }),
    __metadata("design:paramtypes", [typeof (_a = typeof router_1.ActivatedRoute !== "undefined" && router_1.ActivatedRoute) === "function" && _a || Object])
], HashListener);
exports.HashListener = HashListener;
var _a;
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/utils/hash-listener.directive.js.map

/***/ }),

/***/ 164:
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/*
 * Hack while waiting for https://github.com/angular/angular/issues/6595 to be fixed.
 */

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(10);
var router_1 = __webpack_require__(47);
var ScrollSpy = (function () {
    function ScrollSpy(renderer) {
        this.renderer = renderer;
        this.anchors = [];
        this.throttle = false;
    }
    Object.defineProperty(ScrollSpy.prototype, "links", {
        set: function (routerLinks) {
            var _this = this;
            this.anchors = routerLinks.map(function (routerLink) { return "#" + routerLink.fragment; });
            this.sub = routerLinks.changes.subscribe(function () {
                _this.anchors = routerLinks.map(function (routerLink) { return "#" + routerLink.fragment; });
            });
        },
        enumerable: true,
        configurable: true
    });
    ScrollSpy.prototype.handleEvent = function () {
        var _this = this;
        this.scrollPosition = this.scrollable.scrollTop;
        if (!this.throttle) {
            window.requestAnimationFrame(function () {
                var currentLinkIndex = _this.findCurrentAnchor() || 0;
                _this.linkElements.forEach(function (link, index) {
                    _this.renderer.setElementClass(link.nativeElement, "active", index === currentLinkIndex);
                });
                _this.throttle = false;
            });
        }
        this.throttle = true;
    };
    ScrollSpy.prototype.findCurrentAnchor = function () {
        for (var i = this.anchors.length - 1; i >= 0; i--) {
            var anchor = this.anchors[i];
            if (this.scrollable.querySelector(anchor) && this.scrollable.querySelector(anchor).offsetTop <= this.scrollPosition) {
                return i;
            }
        }
    };
    ScrollSpy.prototype.ngOnInit = function () {
        this.scrollable.addEventListener("scroll", this);
    };
    ScrollSpy.prototype.ngOnDestroy = function () {
        this.scrollable.removeEventListener("scroll", this);
        if (this.sub) {
            this.sub.unsubscribe();
        }
    };
    return ScrollSpy;
}());
__decorate([
    core_1.Input("scrollspy"),
    __metadata("design:type", Object)
], ScrollSpy.prototype, "scrollable", void 0);
__decorate([
    core_1.ContentChildren(router_1.RouterLinkWithHref, { descendants: true }),
    __metadata("design:type", typeof (_a = typeof core_1.QueryList !== "undefined" && core_1.QueryList) === "function" && _a || Object),
    __metadata("design:paramtypes", [typeof (_b = typeof core_1.QueryList !== "undefined" && core_1.QueryList) === "function" && _b || Object])
], ScrollSpy.prototype, "links", null);
__decorate([
    core_1.ContentChildren(router_1.RouterLinkWithHref, { descendants: true, read: core_1.ElementRef }),
    __metadata("design:type", typeof (_c = typeof core_1.QueryList !== "undefined" && core_1.QueryList) === "function" && _c || Object)
], ScrollSpy.prototype, "linkElements", void 0);
ScrollSpy = __decorate([
    core_1.Directive({
        selector: "[scrollspy]",
    }),
    __metadata("design:paramtypes", [typeof (_d = typeof core_1.Renderer !== "undefined" && core_1.Renderer) === "function" && _d || Object])
], ScrollSpy);
exports.ScrollSpy = ScrollSpy;
var _a, _b, _c, _d;
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/utils/scrollspy.directive.js.map

/***/ }),

/***/ 165:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(10);
var hash_listener_directive_1 = __webpack_require__(163);
var scrollspy_directive_1 = __webpack_require__(164);
var clarity_angular_1 = __webpack_require__(90);
var common_1 = __webpack_require__(40);
var UtilsModule = (function () {
    function UtilsModule() {
    }
    return UtilsModule;
}());
UtilsModule = __decorate([
    core_1.NgModule({
        imports: [
            common_1.CommonModule,
            clarity_angular_1.ClarityModule.forChild()
        ],
        declarations: [
            hash_listener_directive_1.HashListener,
            scrollspy_directive_1.ScrollSpy
        ],
        exports: [
            hash_listener_directive_1.HashListener,
            scrollspy_directive_1.ScrollSpy
        ]
    })
], UtilsModule);
exports.UtilsModule = UtilsModule;
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/utils/utils.module.js.map

/***/ }),

/***/ 320:
/***/ (function(module, exports, __webpack_require__) {

exports = module.exports = __webpack_require__(38)(false);
// imports


// module
exports.push([module.i, ".clr-icon.clr-clarity-logo {\n  background-image: url(/photon-controller/images/vmw_oss.svg); }\n\n.hero {\n  background-color: #ddd;\n  left: -24px;\n  padding-bottom: 2em;\n  padding-top: 2em;\n  overflow-x: hidden;\n  position: relative;\n  text-align: center;\n  top: -24px; }\n  .hero .btn-custom {\n    display: inline-block;\n    text-align: center;\n    margin: auto; }\n\n@media (min-width: 320px) {\n  .content-area {\n    overflow-x: hidden; }\n  .hero {\n    width: 100vw; } }\n\n@media (min-width: 768px) {\n  .content-area {\n    overflow-x: hidden; }\n  .hero {\n    width: 110%; } }\n\n.hero-image img {\n  max-width: 360px; }\n\n.icon {\n  display: inline-block;\n  height: 32px;\n  vertical-align: middle;\n  width: 32px; }\n  .icon.icon-github {\n    background: url(/photon-controller/images/github_icon.svg) no-repeat left -2px; }\n\n.nav-group label {\n  display: block;\n  margin-bottom: 1em; }\n\n.sidenav .nav-link {\n  padding: 3px 6px; }\n  .sidenav .nav-link:hover {\n    background: #eee; }\n  .sidenav .nav-link.active {\n    background: #d9e4ea;\n    color: #000; }\n\n.section {\n  padding: .5em 0; }\n\n.contributor {\n  border-radius: 50%;\n  border: 1px solid #ccc;\n  margin-bottom: 1.5em;\n  margin-right: 1em;\n  max-width: 64px;\n  text-decoration: none; }\n\n@media (min-width: 320px) {\n  #license {\n    padding-bottom: 20vh; } }\n\n@media (min-width: 768px) {\n  #license {\n    padding-bottom: 77vh; } }\n\n.row:after {\n  clear: both;\n  content: \"\";\n  display: table; }\n", ""]);

// exports


/*** EXPORTS FROM exports-loader ***/
module.exports = module.exports.toString();

/***/ }),

/***/ 329:
/***/ (function(module, exports) {

module.exports = "<clr-main-container>\n    <header class=\"header header-6\">\n        <div class=\"branding\">\n            <a href=\"https://vmware.github.io/\" class=\"nav-link\">\n                <span class=\"clr-icon clr-clarity-logo\"></span>\n                <span class=\"title\">VMware&reg; Open Source Program Office</span>\n            </a>\n        </div>\n    </header>\n    <div class=\"content-container\">\n        <div id=\"content-area\" class=\"content-area\" hash-listener #scrollable>\n            <div class=\"hero\">\n                <div class=\"hero-image\"><img src=\"images/photon-controller.png\" alt=\"VMware Photon Controller&trade;\"></div>\n                <h3>Distributed, multi-tenant host controller and scheduler</h3>\n                <p>\n                    <a href=\"https://github.com/vmware/photon-controller\" class=\"btn btn-primary\"><i class=\"icon icon-github\"></i> Try Photon Controller&trade;</a>\n                    <br>\n                    Have a question? Visit the <a href=\"https://groups.google.com/forum/#!forum/photon-controller\">Photon Controller Google Group</a>\n                </p>\n            </div>\n            <div id=\"overview\" class=\"section\">\n                <h2>What is Photon Controller&trade;</h2>\n\n                <p>Photon Controller&trade; is a distributed, multi-tenant host controller optimized for containers. The Photon Controller delivers:</p>\n\n                <br>\n\n                <ul>\n                    <li><strong>API-first Model</strong>: A user-experience focused on the automation of infrastructure consumption and operations using simple RESTful APIs, SDKs and CLI tooling, all fully multi-tenant. Allows a small automaton-savvy devops team to efficiently leverage fleets of servers.</li>\n                    <li><strong>Fast, Scale-out Control Plane</strong>: A built-from-scratch infrastructure control plane optimized for massive scale and speed, allowing the creation of 1000s of new VM-isolated workloads per minute, and supporting 100,000s of total simultaneous workloads</li>\n                    <li><strong>Integrated PaaS and Native Container Support</strong>: Developer teams consuming infrastructure get their choice of open container orchestration frameworks (e.g. Pivotal CF/Lattice, Kubernetes, Docker Swarm, and Mesos). The Photon controller is built for large environments to run workloads designed for cloud-native (distributed) apps. Examples include a modern scale-out SaaS/mobile-backend apps, highly dynamic continuous integration or simulation environments, sizable data analytics clusters (e.g., Hadoop/Spark), or large-scale platform-as-a-service deployments (e.g., Cloud Foundry).</li>\n                </ul>\n\n            </div>\n\n            <div id=\"download\" class=\"section\">\n                <h2>Getting Photon Controller&trade;</h2>\n\n                <p>If you'd like to try Photon Controller on ESXi the <a href=\"https://github.com/vmware/photon-controller/wiki\">Getting Started Guide</a> will give you step by step instructions to get up and running.</p>\n\n                <p>The source code is distributed under the <a href=\"https://vmware.github.io/photon-controller/files/photon_tech_preview_license_agreement.pdf\">VMware Technology Preview License Agreement</a>. Open source license information may be found in the <a href=\"https://github.com/vmware/photon-controller/blob/master/LICENSE.txt\">Photon Controller open source license file</a>.</p>\n\n                <p>Photon Controller&trade; source code is available at <a href=\"https://github.com/vmware/photon-controller\">Photon Controller’s GitHub source repository</a>. You can build your own Photon Controller by cloning the repo and following the instructions in the <a href=\"https://github.com/vmware/photon-controller/blob/master/README.md\">README.md</a>.</p>\n            </div>\n\n            <div id=\"support\" class=\"section\">\n                <h2>Support</h2>\n                \n                <p>Photon Controller&trade; is released as open source software and comes with no commercial support. However, we want to ensure success and recognize that Photon Controller&trade; consumers might fall into a range of roles - from developers that are steeped in the conventions of open-source to customers that are more accustomed to VMware commercial offerings. We have created a few ways to engage with the Photon Controller team and community:</p>\n\n                <br>\n\n                <ul>\n                    <li>Visit the Photon Controller <a href=\"https://groups.google.com/forum/#!forum/photon-controller\">Google Group</a></li>\n                    <li>Ask code support questions using the \"photon-controller\" tag on <a href=\"http://stackoverflow.com/\">Stack Overflow</a></li>\n                </ul>\n            </div>\n            <div id=\"contributors\" class=\"section\">\n                <h2>Contributors</h2>\n                \n                <p>\n                    <a title=\"Anfernee Gui\" href=\"https://github.com/anfernee\"><img alt=\"Anfernee Gui\" src=\"https://avatars1.githubusercontent.com/u/174200?v=3\" class=\"contributor\"></a>\n                    <a title=\"Ashok Chandrasekar\" href=\"https://github.com/ashoksekar07\"><img alt=\"Ashok Chandrasekar\" src=\"https://avatars0.githubusercontent.com/u/1829544?v=3\" class=\"contributor\"></a>\n                    <a title=\"Bharath Siravara\" href=\"https://github.com/bsiravara\"><img alt=\"Bharath Siravara\" src=\"https://avatars0.githubusercontent.com/u/1231429?v=3\" class=\"contributor\"></a>\n                    <a title=\"Yin Ding\" href=\"https://github.com/dingyin\"><img alt=\"Yin Ding\" src=\"https://avatars2.githubusercontent.com/u/2991945?v=3\" class=\"contributor\"></a>\n                    <a title=\"giskender\" href=\"https://github.com/giskender\"><img alt=\"giskender\" src=\"https://avatars3.githubusercontent.com/u/12159164?v=3\" class=\"contributor\"></a>\n                    <a title=\"jivkodobrev\" href=\"https://github.com/jivkodobrev\"><img alt=\"jivkodobrev\" src=\"https://avatars1.githubusercontent.com/u/5296992?v=3\" class=\"contributor\"></a>\n                    <a title=\"Justin Yu\" href=\"https://github.com/justin-yu\"><img alt=\"Justin Yu\" src=\"https://avatars1.githubusercontent.com/u/3218302?v=3\" class=\"contributor\"></a>\n                    <a title=\"Kenneth Jung\" href=\"https://github.com/kmjung\"><img alt=\"Kenneth Jung\" src=\"https://avatars2.githubusercontent.com/u/10439787?v=3\" class=\"contributor\"></a>\n                    <a title=\"lcastellano\" href=\"https://github.com/lcastellano\"><img alt=\"lcastellano\" src=\"https://avatars3.githubusercontent.com/u/7454168?v=3\" class=\"contributor\"></a>\n                    <a title=\"longzhou\" href=\"https://github.com/longzhou\"><img alt=\"longzhou\" src=\"https://avatars3.githubusercontent.com/u/1777833?v=3\" class=\"contributor\"></a>\n                    <a title=\"liuxuli\" href=\"https://github.com/liuxuli\"><img alt=\"liuxuli\" src=\"https://avatars0.githubusercontent.com/u/8867423?v=3\" class=\"contributor\"></a>\n                    <a title=\"Liang Zhang\" href=\"https://github.com/liangzhang11\"><img alt=\"Liang Zhang\" src=\"https://avatars0.githubusercontent.com/u/13108934?v=3\" class=\"contributor\"></a>\n                    <a title=\"Mihnea Olteanu\" href=\"https://github.com/molteanu\"><img alt=\"Mihnea Olteanu\" src=\"https://avatars1.githubusercontent.com/u/5076121?v=3\" class=\"contributor\"></a>\n                    <a title=\"Pankaj Lakhina\" href=\"https://github.com/Pankaj-lakhina\"><img alt=\"Pankaj Lakhina\" src=\"https://avatars3.githubusercontent.com/u/7528525?v=3\" class=\"contributor\"></a>\n                    <a title=\"Adrian Schroeter\" href=\"https://github.com/schadr\"><img alt=\"Adrian Schroeter\" src=\"https://avatars3.githubusercontent.com/u/278120?v=3\" class=\"contributor\"></a>\n                    <a title=\"Sufian Dar\" href=\"https://github.com/sufiand\"><img alt=\"Sufian Dar\" src=\"https://avatars0.githubusercontent.com/u/11950681?v=3\" class=\"contributor\"></a>\n                    <a title=\"shchang-agent\" href=\"https://github.com/shchang-agent\"><img alt=\"shchang-agent\" src=\"https://avatars2.githubusercontent.com/u/12549218?v=3\" class=\"contributor\"></a>\n                    <a title=\"VIKASH CHANDRA RAI\" href=\"https://github.com/vikashrepo\"><img alt=\"VIKASH CHANDRA RAI\" src=\"https://avatars0.githubusercontent.com/u/7216763?v=3\" class=\"contributor\"></a>\n                    <a title=\"vuil\" href=\"https://github.com/vuil\"><img alt=\"vuil\" src=\"https://avatars3.githubusercontent.com/u/5665855?v=3\" class=\"contributor\"></a>\n                    <a title=\"xiao-zhou\" href=\"https://github.com/xiao-zhou\"><img alt=\"xiao-zhou\" src=\"https://avatars0.githubusercontent.com/u/1831638?v=3\" class=\"contributor\"></a>\n                    <a title=\"yshengvmware\" href=\"https://github.com/yshengvmware\"><img alt=\"yshengvmware\" src=\"https://avatars3.githubusercontent.com/u/12450738?v=3\" class=\"contributor\"></a>\n                    <a title=\"Touseef Liaqat\" href=\"https://github.com/Toliaqat\"><img alt=\"Touseef Liaqat\" src=\"https://avatars1.githubusercontent.com/u/6778940?v=3\" class=\"contributor\"></a>\n                    <a title=\"m1ch1\" href=\"https://github.com/m1ch1\"><img alt=\"m1ch1\" src=\"https://avatars2.githubusercontent.com/u/910840?v=3\" class=\"contributor\"></a>\n                    <a title=\"umerkhan\" href=\"https://github.com/umerkhan\"><img alt=\"umerkhan\" src=\"https://avatars2.githubusercontent.com/u/2228539?v=3\" class=\"contributor\"></a>\n                    <a title=\"Maithem\" href=\"https://github.com/Maithem\"><img alt=\"Maithem\" src=\"https://avatars2.githubusercontent.com/u/7818642?v=3\" class=\"contributor\"></a>\n                    <a title=\"Amarpad\" href=\"https://github.com/Amarpad\"><img alt=\"Amarpad\" src=\"https://avatars2.githubusercontent.com/u/8646765?v=3\" class=\"contributor\"></a>\n                </p>\n            </div>\n\n            <div id=\"contributing\" class=\"section\">\n                <h2>Contributing</h2>\n\n                <p>The Photon Controller project team welcomes contributions from the community. If you wish to contribute code, we require that you first sign our <a href=\"https://vmware.github.io/photon-controller/files/vmware_cla.pdf\">Contributor License Agreement</a> and return a copy to <a href=\"mailto:osscontributions@vmware.com\">osscontributions@vmware.com</a> before we can merge your contribution.</p>\n            </div>\n\n            <div id=\"license\" class=\"section\">\n                <h2>License</h2>\n\n                <p>Photon Controller&trade; is comprised of many open source software components, each of which has its own license that is located in the source code of the respective component as well as documented in the <a href=\"https://github.com/vmware/photon-controller/blob/master/LICENSE.txt\">open source license file</a> accompanying the Photon Controller&trade; distribution.</p>\n\n                <p>The Photon binaries are distributed under the <a href=\"https://vmware.github.io/photon-controller/files/photon_tech_preview_license_agreement.pdf\">VMware Technology Preview License Agreement</a>.</p>\n            </div>\n        </div>\n        <nav class=\"sidenav\" [clr-nav-level]=\"2\">\n            <section class=\"sidenav-content\">\n                <section class=\"nav-group\" [scrollspy]=\"scrollable\">\n                    <label><a class=\"nav-link active\" routerLink=\".\" routerLinkActive=\"active\" fragment=\"overview\">Overview</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"download\">Download</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"support\">Support</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"contributors\">Contributors</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"contributing\">Contributing</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"license\">License</a></label>\n                </section>\n            </section>\n        </nav>\n    </div>\n</clr-main-container>\n"

/***/ }),

/***/ 360:
/***/ (function(module, exports, __webpack_require__) {

module.exports = __webpack_require__(142);


/***/ }),

/***/ 89:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(10);
var router_1 = __webpack_require__(47);
var AppComponent = (function () {
    function AppComponent(router) {
        this.router = router;
    }
    return AppComponent;
}());
AppComponent = __decorate([
    core_1.Component({
        selector: 'my-app',
        template: __webpack_require__(329),
        styles: [__webpack_require__(320)]
    }),
    __metadata("design:paramtypes", [typeof (_a = typeof router_1.Router !== "undefined" && router_1.Router) === "function" && _a || Object])
], AppComponent);
exports.AppComponent = AppComponent;
var _a;
//# sourceMappingURL=/Users/druk/Sites/photon-controller/src/src/src/app/app.component.js.map

/***/ })

},[360]);
//# sourceMappingURL=main.bundle.js.map