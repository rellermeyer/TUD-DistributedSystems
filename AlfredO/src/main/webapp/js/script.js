let map, infoWindow, labelIndex
let markers = []
let labels = []


function geolocate() {
  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(onGeolocateSuccess, onGeolocateError);
  } else {
    handleLocationError(false, infoWindow, map.getCenter());
  }
}

function onGeolocateSuccess(coordinates) {
  const pos = {
    lat: coordinates.coords.latitude,
    lng: coordinates.coords.longitude
  };
  const url = `http://localhost:8080/services?latitude=${pos.lat}&longitude=${pos.lng}`
  $.get(url, function(data, status) { drawServices(data, pos) })
}

function drawServices(services, user_pos) {
  // if (!map) {
  //   initMap(user_pos)
  // }
  cleanMap()
  createMarkerOnMe(user_pos)
  fillMap(services)
  drawServiceList(services)
}

function drawServiceList(services) {
  const service_list = $("#service_list")
  service_list.empty()
  for (const s of services) {
    const html = create_service_template(s)
    service_list.append(html)
  }
}

function create_service_template(service) {
return `
  <article class="level-item box">
      <a href="${service.url}" target="_blank">
        <p>${service.name}</p>
      </a>
  </article>
  `
}

function initMap(pos) {
  map = new google.maps.Map(document.getElementById('map'), {
    center: { lat: -34.397, lng: 150.644 },
    zoom: 13
  })
  infoWindow = new google.maps.InfoWindow;
  window.scrollBy(0, 200);
  infoWindow.open(map);
  map.setCenter(pos);
}

function fillMap(services_array) {
  for (const service of services_array) {
    const lat = parseFloat(service["coordinates"]["latitude"])
    const lng = parseFloat(service["coordinates"]["longitude"])
    createMarker({"lat": lat, "lng": lng}, service["name"])
  }
}

function createMarkerOnMe(position) {
  createMarker(position, "Hi! It's you =)")
}

function createMarker(position, title) {
  const marker = new google.maps.Marker({
    map: map,
    position: position,
    title: title,
    text: title,
    label: labels[labelIndex++ % labels.length]
  })
  marker.setMap(map)
  markers.push(marker)
}

function cleanMap() {
  for (const m of markers) {
    m.setMap(null)
    markers = []
  }
}

function handleLocationError(browserHasGeolocation, infoWindow, pos) {
  infoWindow.setPosition(pos);
  infoWindow.setContent(browserHasGeolocation ?
      'Error: The Geolocation service failed.' :
      'Error: Your browser doesn\'t support geolocation.');
  infoWindow.open(map);
}

function onGeolocateError(error) {
  console.log(error.code, error.message);
  if (error.code === 1) {
    console.log('User declined geolocation');
  } else if (error.code === 2) {
    console.log('Geolocation position unavailable');
  } else if (error.code === 3) {
    console.log('Timeout determining geolocation');
  }
}

document.addEventListener("DOMContentLoaded", () => {
  const $geolocateButton = document.getElementById('geolocation-button');
  $geolocateButton.addEventListener('click', geolocate);
});