import { Component, OnInit,Input,  OnChanges, SimpleChanges, AfterViewInit, OnDestroy } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import Map from 'ol/Map';
import View from 'ol/View';
import {Tile as TileLayer,Vector as VectorLayer} from 'ol/layer';
import * as proj from 'ol/proj';
import {Cluster,OSM,Vector as VectorSource} from 'ol/source';
import Feature from 'ol/Feature';
import {Point,LineString} from 'ol/geom';
import Overlay from 'ol/Overlay';
import {singleClick,doubleClick} from 'ol/events/condition';
import {
	Circle as CircleStyle,
	Fill,
	Text,
	Icon,
	Stroke,
	Style,
  } from 'ol/style';
  import {
	Select,
	defaults as defaultInteractions,
  } from 'ol/interaction';

  declare var jQuery: any;
  import { environment } from "../../../../environments/environment";
import { TopologyGraphService } from 'src/app/common/services/topology-graph.service';
import { TopologyService } from 'src/app/common/services/topology.service';
@Component({
  selector: 'app-world-map-view',
  templateUrl: './world-map-view.component.html',
  styleUrls: ['./world-map-view.component.css']
})
export class WorldMapViewComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  @Input() data:any;
  @Input() reloadMap:boolean;
    map:any;
	linkLayer:any;
	centerLng:number = 0 ;
	centerLat:number =  0;
	markers:any = [];
	graphdata:any;
	links:any; 
	switches:any;
	pops:any=[];
	popLinks:any=[];
	markerSource :any=null;
	clusterLinkLayer:any;
	ClusterLinks:any=[];
	clusterLayer:any;
	clusterLinkSource:any;
	linkSource:any=null;
	clusterSource:any=null;
	linkFeatures:any = [];
	mouseCoordinates:any=null;
	clusterDistance:any=50;
	overlay:any; 
	popInfoOverlay:any;
	oldTranformation:any='';
	oldGraphTranslation:any='';
	container=  document.getElementById('popup');
	content = document.getElementById('popup-content');
	closer = document.getElementById('popup-closer');
	popinfocontainer = document.getElementById('popInfoContainer');
	popinfocontent = document.getElementById('popInfocontent');
	popinfocloser = document.getElementById('popInfocloser');
	 minimise = document.getElementById('popup-minimize');
	 maximise = document.getElementById('popup-maximize');
	graph_loader = document.getElementById('graph_loader');
	default_location:any={
		"pop": "Unknown",
		"datacenter": "Unknown",
		"latitude": 22.8951683,
		"longitude": 147.6138315,
		"country": "TPN",
		"city": "Atlantis"
	};
	selectSingleClick = new Select({
		condition: singleClick, 
	  });
	selectDoubleClick = new Select({
	condition: doubleClick, 
	});
	constructor(private httpClient:HttpClient,private topologyGraphService:TopologyGraphService,private topologyService:TopologyService) { 
  }

	ngOnInit(): void {
		this.topologyService.notifyObj.subscribe((data:any)=>{
			if(data && typeof data.type !='undefined'){
				var type = 'switch';
				if(data.type.includes('isl')){
					type = 'isl';
				}
				switch(type){
					case 'isl': this.highLightPopLink(data.newlink);
							 break;
					case 'switch': this.highLightPop(data.switch);
							 break;
				}
			}
		})
	}

	highLightPopLink(data){
		var self = this;
		var foundFeature = null;
		if(this.ClusterLinks && this.ClusterLinks.length){
			this.ClusterLinks.forEach(link=>{
				var clusterLinkData = link.values_.clusterLinkData;
				if(clusterLinkData && clusterLinkData.length){
					clusterLinkData.forEach(l=>{
						if(l.source_switch == data.source_switch && l.target_switch == data.target_switch && l.src_port == data.src_port && l.dst_port == data.dst_port){
							foundFeature = link;
						}
					})
				}
			})
		}
		if(this.linkSource && !foundFeature){
			Object.keys(this.linkSource.uidIndex_).forEach((link)=>{
				var linksData = self.linkSource.uidIndex_[link].values_.linksData;
				if(linksData && linksData.length){
					linksData.forEach((l)=>{
						if(l.source_switch == data.source_switch && l.target_switch == data.target_switch && l.src_port == data.src_port && l.dst_port == data.dst_port){
							foundFeature = self.linkSource.uidIndex_[link];
						}
					})
				}
			})
		}
		// if link is inside pop
		var linkInsidePop = null;
		if(this.clusterSource && this.clusterSource.features && this.clusterSource.features.length){
			var clusterFeatures = this.clusterSource.features;
			clusterFeatures.forEach(feature => {
					var valueFeatures = feature.values_.features;
					valueFeatures.forEach(f=>{
						var featureLinks = f.values_.links;
						featureLinks.forEach(l => {
							if(l.source_switch == data.source_switch && l.target_switch == data.target_switch && l.src_port == data.src_port && l.dst_port == data.dst_port){
								linkInsidePop = feature;
							}
						});
					});
					
			});
		}
		if(foundFeature){
			var oldStyle =foundFeature.getStyle();
			var style = new Style({
				stroke: new Stroke({
				  color:'rgba(255, 0, 0, '+0.5+')',
				  width: 10
				})
			});
			foundFeature.setStyle(style);
			setTimeout(()=>{
				foundFeature.setStyle(oldStyle);
			},2000);
		}
		if(linkInsidePop){
			var oldStyle = linkInsidePop.getStyle();
			var style =  new Style({
				image:new CircleStyle({
				  radius: 5,
				  fill: new Fill({color: 'white'}),
				  stroke: new Stroke({
					color: 'rgba(255, 0, 0, '+0.5+')',
					width: 20,
				  })
				}),
			  });
			  linkInsidePop.setStyle(style);
			  setTimeout(()=>{
				linkInsidePop.setStyle(oldStyle);
			  },2000);
		}
	}

	highLightPop(data){
		var self = this;
		var popFeature = null;
		if(this.clusterSource && this.clusterSource.features && this.clusterSource.features.length){
			var clusterFeatures = this.clusterSource.features;
			clusterFeatures.forEach(feature => {
					var valueFeatures = feature.values_.features;
					valueFeatures.forEach(f=>{
						var featureSwitches = f.values_.switches;
						featureSwitches.forEach(d => {
							if(d.switch_id == data.switch_id){
								popFeature = feature;
							}
						});
					});
					
			});
		}
		if(popFeature){
			var oldStyle = popFeature.getStyle();
			var style =  new Style({
				image:new CircleStyle({
					radius: 5,
					fill: new Fill({color: 'white'}),
					stroke: new Stroke({
					color: 'rgba(255, 0, 0, '+0.5+')',
					width: 20,
					})
				}),
				});
				popFeature.setStyle(style);
				setTimeout(()=>{
				popFeature.setStyle(oldStyle);
				},2000);
		}
	}

	getPopLinks(switches,links){
		var switchIds = switches.map((d)=>{
			return d.switch_id;
		});
		var isls = [];
		if(links && links.length){
			links.forEach(link=>{
				if(switchIds.indexOf(link.source_switch) > -1 && switchIds.indexOf(link.target_switch) > -1){
					isls.push(link);
				}
			})
		}
		return isls;
	}

	groupBy(objectArray, property) {
		var self = this;
		return objectArray.reduce((acc, obj) => {
			var keyValue = obj[property];
			if(!(keyValue.latitude && keyValue.longitude)){
				obj[property] = self.default_location; 
		   }
		   var key = obj[property].latitude+"_"+obj[property].longitude;
		   if (!acc[key]) {
			  acc[key] = [];
		   }		  
		   acc[key].push(obj);
		   return acc;
		}, {});
	 }
	 checkIfAlreadyAdded(popLinks,linkObj){
		 var flag = false;
		 for(var i =0; i < popLinks.length; i++){
			 var src_dst_id = popLinks[i].src + "_"+popLinks[i].trgt;
			 var dst_src_id = popLinks[i].trgt + "_"+popLinks[i].src;
			 var linkSrc_dst_id = linkObj.src+"_"+linkObj.trgt;
			 if(src_dst_id == linkSrc_dst_id || dst_src_id == linkSrc_dst_id){
				 flag = true;
				 break;
			 }
		 }
		 return flag;
	 }
	ngOnChanges(change:SimpleChanges){
		if( typeof(change.data)!='undefined' && change.data){
			if(typeof(change.data)!=='undefined' && change.data.currentValue){
			  this.data  = JSON.parse(JSON.stringify(change.data.currentValue));
			  if(this.data && this.data.switch && this.data.switch.length){
				this.pops = [];
				this.popLinks = [];
				this.links = this.data.isl;
				this.switches = this.data.switch;
				var popWiseData = this.groupBy(this.switches,'location');
				Object.keys(popWiseData).forEach((key)=>{
					var switchIds = popWiseData[key].map(s=>{ return s.switch_id;});
					var pops = popWiseData[key].map(s=>{
						if(s.pop =='' || s.pop =='undfined'){
							return "Unknown";
						} 
						return s.pop;
					}).filter((value,i,self)=>{ return self.indexOf(value) === i; }).join(",");
					var links = this.getPopLinks(popWiseData[key],this.links);
					var d = {"id":key,
							"pops":pops,
							"switches":popWiseData[key],
							"location":popWiseData[key][0].location,
							"links":links,
							"switchIds":switchIds
						};
					this.pops.push(d);
				});

				// fetching the links between pops

				for(var i=0; i < this.pops.length; i++){
					var sourcePop = this.pops[i];
					for(var j=0; j< this.pops.length; j++){
						if(i!=j){
							var targetPop = this.pops[j];
							var lnkObj = this.getLinkObjInPops(sourcePop,targetPop);
							if(lnkObj && lnkObj.hasOwnProperty('source') && !this.checkIfAlreadyAdded(this.popLinks,lnkObj)){
								this.popLinks.push(lnkObj);
							}
						}
					}
				}
			}
			}
			if(this.reloadMap){
				this.reloadWorldMap();
			}else{
				this.initMap();
			}
			
		  }
		
	}

	ngOnDestroy(){

	}
	ngAfterViewInit(){
		setTimeout(()=> {
			this.map.updateSize();
		}, 100);
	}


  	initMap(){
	  this.overlay = new Overlay({
			element: this.container,
			autoPan: true,
			autoPanAnimation: {
				duration: 250,
			},
		});
		this.popInfoOverlay = new Overlay({
			element: this.popinfocontainer,
			autoPan: true,
			autoPanAnimation: {
				duration: 250,
			},
		});
	  this.map =  new Map({
		layers: [
		  new TileLayer({
			source: new OSM()
		  })
		],
		target: 'world_map',
		overlays: [this.overlay,this.popInfoOverlay],
		view: new View({
		  center: [0, 0],
		  zoom: 2,
		  minZoom: 2,
		  maxZoom:20,
		})
	  });
	this.map.addInteraction(this.selectSingleClick); 
	this.map.addInteraction(this.selectDoubleClick);
	var view = this.map.getView();
	view.setCenter(proj.fromLonLat([this.centerLng, this.centerLat]));
	this.loadEvents();	
	this.loadLinks(this.popLinks);
	this.loadMarkersClusters();	
  }

  reloadWorldMap(){
	setTimeout(()=>{
		if(typeof this.clusterLayer !='undefined' && typeof this.clusterLayer.getSource() !='undefined'){
			this.clusterLayer.getSource().clear();	
		 }
		  if(typeof this.clusterLinkLayer !='undefined' && typeof this.clusterLinkLayer.getSource() !='undefined'){
			this.clusterLinkLayer.getSource().clear();	
		 }
		 if(typeof this.linkLayer !='undefined' && typeof this.linkLayer.getSource() !='undefined'){
			this.linkLayer.getSource().clear();	
		 }
		 this.clusterSource.clear();
		 this.markerSource.clear();
		 this.markers = [];
		 this.loadLinks(this.popLinks);
		 this.loadMarkersClusters();
	},200);
	
  }
 minimizePopup(){
			this.maximise.style.display="block";
			this.minimise.style.display="none";
			this.container.style.width="400px";
			this.container.style.height="360px";			
			var svg = this.content.querySelector('svg');
			svg.setAttribute('width',"380");
			svg.setAttribute('height' , "345px");
			this.container.parentElement.style.transform = this.oldTranformation;
			this.graph_loader.style.display="block";
			setTimeout(() => {				
				this.topologyGraphService.zoomFit();
				this.graph_loader.style.display="none";
			}, 2000);
			return false;
 }

 maximizePopup(){
	this.minimise.style.display="block";
	this.maximise.style.display="none";
	var rootDiv = document.getElementById('worldmap');
	var svg = this.content.querySelector('svg');
	var width = rootDiv.offsetWidth;
	var height = rootDiv.offsetHeight;
	var svgWidth = (width-140);
	var svgHeight = (height-140);
	var leftTrans = rootDiv.offsetLeft;
	this.container.style.width=(width-100)+"px";
	this.container.style.height=(height-100)+"px";
	svg.setAttribute('width',svgWidth.toString());
	svg.setAttribute('height',svgHeight.toString());
	this.graph_loader.style.display="block";
	this.oldTranformation = this.container.parentElement.style.transform;
	this.container.parentElement.style.transform = "translate(0%, 0%) translate("+(leftTrans+100)+"px, "+(height-50)+"px)";
	setTimeout(() => {
		this.graph_loader.style.display="none";
		var widthTrans = svgWidth / 2;
		var heightTrans = svgHeight / 2;
		this.topologyGraphService.zoomFit(widthTrans,heightTrans);
	}, 2000);
	return false;
 }

  loadEvents(){
	var self = this;
	if(this.closer){
		this.closer.onclick= (()=>{
			this.overlay.setPosition(undefined);
			this.closer.blur();
			this.minimizePopup();
			return false;	
		});
	}

	if(this.popinfocloser){
		this.popinfocloser.onclick= (()=>{
			this.content.innerHTML = "";
			this.popInfoOverlay.setPosition(undefined);
			this.popinfocloser.blur();
			return false;	
		});
	}

	if(this.minimise){
		this.minimise.onclick= ((e)=>{
				this.minimizePopup();
		});
	}

	if(this.maximise){
		this.maximise.onclick= ((e)=>{
			this.maximizePopup();	
		});
	}
	this.map.on('pointermove',(evt)=>{
		var pixel = evt.pixel;

		var feature = this.map.forEachFeatureAtPixel(pixel, function(feature) {
			return feature;
		});
		if(feature && feature.values_ && typeof feature.values_.features !='undefined' && feature.values_.features.length ==1){
			var featureValues = feature.values_.features[0].values_;
			if(featureValues && featureValues.type == 'marker'){
				var coordinate = feature.values_.features[0].getGeometry().getCoordinates();
				self.popinfocontent.innerHTML = "";
				var html = "<div class='col-md-12'><div class='form-group'><label><b>Pop: </b></label><span>"+featureValues.pop+"</span></div>";
				html+= "<div class='form-group'><label><b>City: </b></label><span>"+featureValues.city+"</span></div><div class='form-group'><label><b>Country: </b></label><span>"+featureValues.country+"</span></div></div>";
				self.popinfocontent.innerHTML = html;
				self.popInfoOverlay.setPosition(coordinate);
						
			}
		}else{
			self.popinfocontent.innerHTML = "";
			self.popInfoOverlay.setPosition(undefined);
		}
	})
	var currZoom = this.map.getView().getZoom();

	this.map.on('moveend', (e)=> {
	var newZoom = this.map.getView().getZoom();	
	if (currZoom != newZoom) {
		setTimeout(()=>{
		this.enableLinks();
		this.loadCLusterLinks();
		},500);
	}
	});
	this.map.on('click',(evt)=>{
		this.mouseCoordinates = evt.coordinate;
		if(this.overlay.getPosition()){
			this.content.innerHTML = "";
			this.overlay.setPosition(undefined);
			this.minimizePopup();
			this.closer.blur();
		}
	})
	this.selectDoubleClick.on('select',(evt)=>{
		if(evt.target.getFeatures().getLength() > 0){
			var features = evt.target.getFeatures().getArray();
			if(typeof features[0].values_.features !='undefined' && features[0].values_.features.length > 1){
			   var Clustercoordinate = features[0].getGeometry().getCoordinates();
			   var view = self.map.getView();
			   var zoomLevel = view.getZoom();
			   if(zoomLevel < 7){
				   zoomLevel = 7;
			   }else{
				   zoomLevel = zoomLevel +1;
			   }
			   view.setZoom(zoomLevel);
			   view.setCenter(Clustercoordinate);
			}
		}
	})
	this.selectSingleClick.on('select', (evt) =>{
		  if(evt.target.getFeatures().getLength() > 0){
			 var features = evt.target.getFeatures().getArray();
			 if(!(typeof features[0].values_.features != 'undefined' && features[0].values_.features.length > 1)){
				if(features[0].values_ && typeof features[0].values_.features !='undefined'){
					var featuresValues = features[0].values_.features[0].values_;
					if(featuresValues.type=="marker"){
						var coordinate = features[0].getGeometry().getCoordinates();
						 self.content.innerHTML = "";
						 self.graph_loader.style.display="block";
						self.overlay.setPosition(coordinate);
						this.getPopupHtml(featuresValues.switches,featuresValues.links);
					}
				}else if(features[0].values_ && typeof(features[0].values_.type)!='undefined' && (features[0].values_.type == 'line' || features[0].values_.type == 'cluster_line')){
					var featuresValues = features[0].values_;
					self.graph_loader.style.display="none";
					self.content.innerHTML = "";
					self.content.innerHTML = this.getIslHtml(featuresValues);
					self.overlay.setPosition(this.mouseCoordinates);
				}
				
			 }				
		 }
	  });
  }
  getPopLinksStatus(features){
	var featuresIds = [];
	  if(features && features.length && features.length > 1){
		  features.forEach((f)=>{
			var values = f.values_;
			var id = values.id;
			featuresIds.push(id);
		  })
	  }
	  var status = "DISCOVERED";
	  for(var i = 0; i < this.popLinks.length; i++){
		  var link = this.popLinks[i];
		   if(featuresIds.indexOf(link.src) > -1 && featuresIds.indexOf(link.trgt) > -1){
				status = link.status;
				if(status == "FAILED")
				break;
		  }
	  }
	  return status;
  }
  getStatusOfPops(features){
	  var status = "DISCOVERED";
	  if(features && features.length){
		  features.forEach((f)=>{
			var values = f.values_;
			var id = values.id;
			if(values.status == "FAILED"){
				status = "FAILED";
			}
			
			
		  })
	  }
	  return status;
  }

  getPopStatus(switches,links){
	  var state = "DISCOVERED";
	  switches.forEach(s=>{
		  if(s.state =="DEACTIVATED"){
			  state = "FAILED";
		  }
	  })
	  links.forEach(l=>{
		if(l.state =="FAILED"){
			state = "FAILED";
		}
	});
	return state;
  }

  loadMarkersClusters(){
	var self =this;
	if(this.pops && this.pops.length){
		this.pops.forEach((data:any,i)=>{
			var popState = this.getPopStatus(data.switches,data.links);
			this.markers[i]  = new Feature({
				geometry:new Point(proj.fromLonLat([data.location.longitude,data.location.latitude])),
				type:'marker',
				pop:data.pops,
				id:data.id,
				status:popState,
				switches:data.switches,
				links:data.links,
				city:data.location.city,
				country:data.location.country

			});
		});
	}
	this.markerSource = new VectorSource({
		features:  self.markers
	  });
	this.clusterSource = new Cluster({
		distance:parseInt(this.clusterDistance, 10),
		source: this.markerSource,
	});
	var vectorLayer= this.clusterLayer = new VectorLayer({
		source: this.clusterSource,
		style: function (feature) {
			var size = feature.get('features').length;
			var status  ="DISCOVERED";
			var statusPop = self.getStatusOfPops(feature.get('features'));
			if(size > 1){
				status = self.getPopLinksStatus(feature.get('features'));
			}
			if(statusPop == "FAILED"){
				status = "FAILED";
			}
			if(size > 1){
				var icon =new Icon({
					src: environment.assetsPath+'/images/green.png',
				   	scale: 0.4
				  });
				if(status == "FAILED"){
					icon =new Icon({
						src: environment.assetsPath+'/images/red.png',
						scale: 0.4
					  });
				}				
				var style = new Style({
					image: icon,
					text: new Text({
					text: size.toString(),
					fill: new Fill({
						color: '#000',
					}),
					}),
				});
			}else{
				var color = "black";
				if(status == 'FAILED'){
					color = 'red';
				}
				var style =  new Style({
					image:new CircleStyle({
					  radius: 5,
					  fill: new Fill({color: 'white'}),
					  stroke: new Stroke({
						color: color,
						width: 4,
					  })
					}),
				  });
			}
			return style;	
		  },
	  });
	this.map.addLayer(vectorLayer);
	vectorLayer.setZIndex(5,10);
	setTimeout(()=>{
		this.loadCLusterLinks();
	},500);
	  
  }
 
  getIslHtml(values){
	  var linksData = typeof(values.linksData) !='undefined' ? values.linksData : [];
	  
	  var html="<div class='table-wrapper-scroll-y my-custom-scrollbar'><table  class='table table-bordered table-striped mb-0'><thead><th>Src Switch</th><th>Src Port</th><th>Dst Switch</th><th>Dst Port</th><th>Status</th><thead><tbody>";
	  if(linksData.length > 0){
		linksData.forEach(link=>{
			var url = "isl/switch/isl/" + link.source_switch+"/"+link.src_port+"/"+link.target_switch+"/"+link.dst_port;
			html+= "<tr  class='cursor-pointer islLink'><td><a href='"+url+"' target='_blank'>"+link.source_switch_name+"</a></td><td><a href='"+url+"' target='_blank'>"+link.src_port+"</a></td><td><a href='"+url+"' target='_blank'>"+link.target_switch_name+"</a></td><td><a href='"+url+"' target='_blank'>"+link.dst_port+"</a></td><td><a href='"+url+"' target='_blank'>"+link.state+"</a></td></tr>";
		});
	  }else if(values.clusterLinkData && values.clusterLinkData.length){
		  var links = values.clusterLinkData;
		  links.forEach(link=>{
					var url = "isl/switch/isl/" + link.source_switch+"/"+link.src_port+"/"+link.target_switch+"/"+link.dst_port;
					html+= "<tr class='cursor-pointer islLink'><td><a href='"+url+"' target='_blank'>"+link.source_switch_name+"</a></td><td><a href='"+url+"' target='_blank'>"+link.src_port+"</a></td><td><a href='"+url+"' target='_blank'>"+link.target_switch_name+"</a></td><td><a href='"+url+"' target='_blank'>"+link.dst_port+"</a></td><td><a href='"+url+"'  target='_blank'>"+link.state+"</a></td></tr>";
				});
	  }
	  html+="</tbody></table></div>";
	  return html;
  }
  getPopupHtml(switches,links){	
	  this.graphdata = {nodes:switches,links:links};
      var margin = {top: 10, right: 30, bottom: 60, left: 40},
 	  width = this.content.offsetWidth || 400 - margin.left - margin.right,
	  height = this.content.offsetHeight  || 400 - margin.top - margin.bottom;
	  this.topologyGraphService.loadworldMapGraph(this.graphdata,'popup-content',width,height,this.graph_loader);	
  }  

  
  enableLinks(){
	  if(this.clusterSource && this.clusterSource.features && this.clusterSource.features.length){
		
		var LinkArr = [];
		this.clusterSource.features.forEach((f)=>{
			if(f.values_.features.length == 1){
				var featureValues = f.values_.features[0].values_;
				LinkArr.push(featureValues.id);
			}
		});
		if(this.linkSource){
			Object.keys(this.linkSource.uidIndex_).forEach((l)=>{
				var src = this.linkSource.uidIndex_[l].values_.source;
				var dst = this.linkSource.uidIndex_[l].values_.target;
				if(LinkArr.indexOf(src) >= 0 && LinkArr.indexOf(dst) >= 0 ){
					this.linkSource.uidIndex_[l].values_.finished = true;
				}else{
					this.linkSource.uidIndex_[l].values_.finished = false;
				}
			})
			setTimeout(()=>{
				this.linkLayer.getSource().changed();
			},500);
		}
		
	  }
  }
  
  loadCLusterLinks(){
	  if(this.clusterSource && this.clusterSource.features && this.clusterSource.features.length){
		var clusterFeatures = this.clusterSource.features;
		this.ClusterLinks = [];
		if(this.linkSource){
		for(var i=0; i < clusterFeatures.length; i++){
			var source = clusterFeatures[i];	
			var linkArr = [];
			if(source.values_.features.length > 0){
				var sourceFeatures = source.values_.features;
				sourceFeatures.forEach((sf)=>{
					linkArr.push(sf.values_.id);
				})	
			}
			
			for(var j=0; j < clusterFeatures.length; j++){
			 if(i!=j){
				var target =  clusterFeatures[j];
				var linkTargetArr = [];
				if(target.values_.features.length > 1){
						var targeteFeatures = target.values_.features;
						targeteFeatures.forEach((sf)=>{
							linkTargetArr.push(sf.values_.id);
						})	
					}				
					var hasLink = false;
					var no_of_links = 0;
					var link_status_failed = false;
					var linksData = [];
					this.popLinks.forEach((link)=>{
						var src = link.src;
						var dst = link.trgt;
						var linkStatus = link.status;
						if(linkArr.indexOf(src) > -1 && linkTargetArr.indexOf(dst) > -1){
							hasLink = true;
							var links = link.links;
							if(links.length){
								links.forEach((l) => {
										linksData.push(l);									
								});
							}
							if(linkStatus == 'FAILED'){
								link_status_failed = true;
							}
						}else if(linkTargetArr.indexOf(src) > -1 && linkArr.indexOf(dst) > -1){
							hasLink = true;
							var links = link.links;
							if(links.length){
								links.forEach((l) => {
										linksData.push(l);									
								});
							}
							if(linkStatus == 'FAILED'){
								link_status_failed = true;
							}
						}
					});
					no_of_links=linksData.length;
					if(hasLink){
						var start_point = source.getGeometry().getCoordinates();
							var end_point = target.getGeometry().getCoordinates();
							var line = new LineString([start_point,end_point]);
						var color = "#00aeff";
						if(link_status_failed){
								color= "#d93923";
							}
							var feature = new Feature({
							geometry: line,
							finished: true,
							type:'cluster_line',
							clusterLinkData:linksData,
							color:color,
							no_links:no_of_links.toString()
							});
							this.ClusterLinks.push(feature);
					}				
			    }
			}
				
			setTimeout(()=>{
				if(typeof this.clusterLinkLayer !='undefined' && typeof this.clusterLinkLayer.getSource() !='undefined'){
					this.clusterLinkLayer.getSource().clear();	
				 }
				this.clusterLinkSource = new VectorSource({
					features:  this.ClusterLinks
				  });
				this.clusterLinkLayer = new VectorLayer({
					source:this.clusterLinkSource,
					style: function (feature) {
						var no_links = feature.get('no_links');
						var color = feature.get('color');
						return new Style({
							stroke: new Stroke({
							  color: color,
							  width: 2
							}),					
							text: new Text({
								text: no_links,
								font: '10px Arial, sans-serif',
								fill: new Fill({ color: 'black' }),
								stroke: new Stroke({ color: 'black', width: 0.5 })
							  }),
						  });
					  }
				})
				this.map.addLayer(this.clusterLinkLayer);
				this.clusterLinkLayer.setZIndex(2,10);
			 },100);
		}
	  }
	}
  }
  getLinkObjInPops(srcPop,targetPop){
	  var hasLink = false;
	  var no_of_links = 0;
	  var status = "DISCOVERED";
	  var links = [];
	  this.links.forEach(l=>{
		if(srcPop.switchIds.indexOf(l.source_switch) > -1 && targetPop.switchIds.indexOf(l.target_switch) > -1){
			hasLink = true;
			no_of_links = no_of_links+1;
			links.push(l);
			if(l.state == 'FAILED'){
				status = "FAILED";
			}
		}else if(targetPop.switchIds.indexOf(l.source_switch) > -1 && srcPop.switchIds.indexOf(l.target_switch) > -1){
			hasLink = true;
			no_of_links = no_of_links+1;
			links.push(l);
			if(l.state == 'FAILED'){
				status = "FAILED";
			}
		}
	  });
	  if(hasLink){
		  return {
			  		"source":srcPop.location,
					"target":targetPop.location,
					"no_of_links":no_of_links.toString(),
					"status":status,
					"src":srcPop.id,
					"trgt":targetPop.id,
					"links":links
				};
	  }
	  return {};

  }
  loadLinks(links){
	var self = this;
   	if(links && links.length){
	   links.forEach((link,i)=>{
		  var start_point = proj.transform([link.source.longitude,link.source.latitude], 'EPSG:4326', 'EPSG:3857');
		  var end_point = proj.transform([link.target.longitude,link.target.latitude], 'EPSG:4326', 'EPSG:3857');
		  var line = new LineString([start_point,end_point]);
			 var linksVal = link.no_of_links; 
			 var color = "#00aeff";
			 var status = link.status;
		  if(status =="FAILED"){
			  color= "#d93923";
		  }
            var feature = new Feature({
			  geometry: line,
			  finished: false,
			  type:'line',
			  status:status,
			  links:linksVal,
			  color:color,
			  source:link.src,
			  target:link.trgt,
			  linksData:link.links
            });
           this.linkFeatures.push(feature);
	   });
	   this.addlinks();
   }	
 }

 addlinks(){
	 setTimeout(()=>{
		this.linkSource = new VectorSource({
			features:  this.linkFeatures
		  });
		this.linkLayer = new VectorLayer({
			source:this.linkSource,
			style: function (feature) {
				var color = feature.get('color');				
				var links = feature.get('links');
				if (feature.get('finished')) {
				  return new Style({
					stroke: new Stroke({
					  color: color,
					  width: 2
					}),					
					text: new Text({
						text: links,
						font: '10px "Arial, sans-serif',
						fill: new Fill({ color: 'black' }),
						stroke: new Stroke({ color: 'black', width:0.5 })
					  })
				  });
				} else {
				  return null;
				}
			  }
		})
		this.map.addLayer(this.linkLayer);
		this.linkLayer.setZIndex(2,10);
	 },100);
 }

}
