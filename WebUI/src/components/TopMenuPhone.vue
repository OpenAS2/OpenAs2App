<template>
  <nav class="navbar navbar-dark fixed-top bg-dark flex-md-nowrap p-0 shadow">
    <a class="navbar-brand col-sm-3 col-md-2 mr-0" href="#">
      <div class="d-flex justify-content-between display-menu2">
        <span>OpenAS2</span>
        <b-icon
          @click="openSidemenu"
          font-scale="1.5"
          class="p-2"
          icon="menu-button-wide"
        ></b-icon>
      </div>
    </a>
    <ul class="navbar-nav px-3">
      <div v-if="showMenu" class="display-menus">
        <ul class="nav flex-column">
          <li
            class="nav-item"
            v-bind:key="route.path"
            v-for="route in routes"
            @click="closedMenuMovil"
          >
            <router-link
              class="nav-link"
              :class="{ active: actualRoute == route.name }"
              :to="route.path"
            >
              {{ route.name }}
            </router-link>
          </li>
          <li @click="showLogout=!showLogout" class="nav-item text-rap pb-2">
            <b-avatar variant="primary" :text="abbreviationName" size="sm"></b-avatar>
            <small class="mx-2 text-white">{{userName}}</small>
            <b-icon v-if="!showLogout"  variant="white" icon="caret-down-fill"></b-icon>
            <b-icon v-else variant="white" icon="caret-up-fill"></b-icon>
          </li>
          <li v-if="showLogout">
            <a class="nav-link pl-3" href="#" v-on:click="logout">Sign out</a>
          </li>
        </ul>
      </div>
    </ul>
  </nav>
</template>
<script>
import store from "../store";
import router from "../routes";

export default {
  name: "top-menu-phone",
  data: function () {
    //console.log(router.options.routes);
    return {
      routes: router.options.routes.filter((e) =>
        e.meta != null ? e.meta.sidemenu : false
      ),
      showLogout:false
    };
  },
  methods: {
    logout: function (e) {
      store.dispatch("logoutAction", {});
      e.preventDefault();
    },
  },
  props: {
    closedMenuMovil: {
      require: false,
    },
    openSidemenu: {
      require: false,
    },
    showMenu: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    actualRoute() {
      return this.$router.name;
    },
    userName(){
      return store.state.username;
    },
    abbreviationName(){
      return store.state.username.substr(0, 2).toUpperCase();
    }
  },
};
</script>
<style>
/*
 * Navbar
 */

.navbar-brand {
  padding-top: 0.75rem;
  padding-bottom: 0.75rem;
  font-size: 1rem;
  background-color: rgba(0, 0, 0, 0.25);
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.25);
}

.navbar .form-control {
  padding: 0.75rem 1rem;
  border-width: 0;
  border-radius: 0;
}

.form-control-dark {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.1);
}

.form-control-dark:focus {
  border-color: transparent;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.25);
}

@media (max-width: 575px) {
  #topMenuPhone {
    display: block !important;
  }
}
@media (min-width: 576px) {
  #topMenuPhone {
    display: none !important;
  }
}
</style>