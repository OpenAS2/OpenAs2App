<template>
<div class="layout">
    <top-menu/>
    <div class="container-fluid">
        <div class="row">
            <!--  <p> -->
                <!-- use router-link component for navigation. -->
                <!-- specify the link by passing the `to` prop. -->
                <!-- `<router-link>` will be rendered as an `<a>` tag by default -->
            <!--   <router-link to="/foo">Go to Foo</router-link>   -->
            <!--   <router-link to="/bar">Go to Bar</router-link>   -->
            <!-- </p> -->
            <side-menu/>
            <!-- route outlet -->
            <!-- component matched by the route will render here -->
            <main role="main" class="col-md-9 ml-sm-auto col-lg-10 px-4">
                <router-view></router-view>
            </main>
        </div>
    </div>
</div>
</template>
<script>
import TopMenu from './TopMenu'
import SideMenu from './SideMenu'
export default {
    name: 'admin-layout',
    components: {
        TopMenu,
        SideMenu
    }
}
</script>
<style scoped>

</style>