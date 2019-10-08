import Vue from 'vue'
import VueRouter from 'vue-router'
import Dashboard from './components/Dashboard'
import CertificatesAdmin from './components/CertificatesAdmin'
import PartnersAdmin from './components/PartnersAdmin'
import ConnectionsAdmin from './components/ConnectionsAdmin'

Vue.use(VueRouter);
const routes = [
    { path: '/', component: Dashboard, name: "Dashboard", meta: { sidemenu: true } },
    { path: '/partners', component: PartnersAdmin, name: "Partners" , meta: { sidemenu: true }},
    { path: '/partner/:id', component: PartnersAdmin, name: "PartnerEditor", meta: { sidemenu: false } },
    { path: '/partnerships', component: ConnectionsAdmin, name: "Connections", meta: { sidemenu: true } },
    { path: '/partnership/:id', component: ConnectionsAdmin, name: "ConnectionEditor", meta: { sidemenu: false } },
    { path: '/certs', component: CertificatesAdmin, name: "Certificates", meta: { sidemenu: true } },
    { path: '/cert/:id', component: CertificatesAdmin, name: "CertificateEditor", meta: { sidemenu: false } }
];
const router = new VueRouter({
  routes // short for `routes: routes`
});
export default router;