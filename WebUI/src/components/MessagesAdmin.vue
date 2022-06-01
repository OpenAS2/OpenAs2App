<template>
  <div id="messages">
    <b-breadcrumb v-if="isPathMessageList" :items="breadcrumbs"></b-breadcrumb>
    <div
      class="
        d-flex
        justify-content-between
        flex-wrap flex-md-nowrap
        align-items-center
        pt-3
        pb-2
        mb-3
        border-bottom
      "
    >
      <h1 class="h2">Messages</h1>
      <div class="btn-toolbar mb-2 mb-md-0">
        <div class="btn-group mr-2">
          <!-- @click="newObject()" -->
          <b-button v-if="isPathMessageList" variant="primary" size="sm">
            <b-icon icon="arrow-repeat"></b-icon>
            Refresh</b-button
          >
          <b-button @click="exportPdf" variant="danger" size="sm">
            <b-icon icon="file-earmark-pdf-fill"></b-icon>
            Export</b-button
          >
          <b-button @click="exportExcel" variant="success" size="sm">
            <b-icon icon="file-earmark-excel-fill"></b-icon>
            Export</b-button
          >
          <!-- <button type="button" class="btn btn-sm btn-outline-secondary">Export</button> -->
        </div>
      </div>
    </div>
    <filter-message-table
      :filter="filter"
      @filterItems="filterItems"
    ></filter-message-table>
    <div id="table-message">
      <table-custom-message
        ref="table-messages"
        :items="getFilterItems"
        :fields="fields"
        :filter="filter"
        :showFilter="showFilter"
        @deleteObject="deleteObject"
        @editObject="editObject"
        @showObject="showObject"
        ><div class="frame">
          <slot name="filter">Title</slot>
        </div>
      </table-custom-message>
    </div>
    <b-modal hide-footer :id="infoModal.id" :title="infoModal.title" ok-only>
      <form-custom
        v-if="infoModal.title"
        title="Message Editor"
        :schema="schema"
        :model="item"
        @commit="saveObject"
        @revert="resetModal"
      >
      </form-custom>
    </b-modal>
  </div>
</template>
<script>
import FormCustom from "./FormCustom";
import TableCustomMessage from "./TableCustomMessage";
import FilterMessageTable from "./FilterMessageTable";

import Utils from "../utils";
import Swal from "sweetalert2";
var _ = require("lodash");
import { jsPDF } from "jspdf";
import autoTable from "jspdf-autotable";
import moment from 'moment';
var XLSX = require("xlsx");

export default {
  data: function () {
    return {
      breadcrumbs: [
        {
          text: "Dashboard",
          to: { name: "Dashboard" },
          active: false,
        },
        {
          text: "Messages",
          to: { name: "Messages" },
          active: true,
        },
      ],
      filter: {
        text: "",
        location: "",
        local: "",
        displays: ["Display Ok", "Display Pending", "Display Stop"],
      },
      showFilter: false,
      filterField: [
        {
          type: "select",
          name: "location",
          label: "Remote Location",
          // placeholder: "X.509 Certificate Alias on the KeyStore",
          _validate: {
            required: true,
          },
          list: [],
        },
        {
          type: "select",
          name: "message",
          label: "Message ID",
          // placeholder: "X.509 Certificate Alias on the KeyStore",
          _validate: {
            required: true,
          },
          list: [],
        },
        {
          type: "checkbox",
          name: "displayOk",
          label: "Display Ok",
          // placeholder: "X.509 Certificate Alias on the KeyStore",
        },
        {
          type: "checkbox",
          name: "displayPending",
          label: "Display Pending",
          // placeholder: "X.509 Certificate Alias on the KeyStore",
        },
        {
          type: "checkbox",
          name: "displayStopped",
          label: "Display Stopped",
          // placeholder: "X.509 Certificate Alias on the KeyStore",
        },
      ],
      schema: {
        fields: [
          {
            key: "name",
            type: "text",
            name: "name",
            label: "Message ID",
            placeholder: "Trading Message's Unique Key Name",
            _validate: {
              required: true,
            },
          },
          {
            type: "text",
            name: "as2_id",
            label: "AS2 ID",
            placeholder: "Trading Message's AS2 ID",
            _validate: {
              required: true,
            },
          },
          {
            type: "email",
            name: "email",
            label: "Email",
            placeholder: "Trading Message's Email Address",
            _validate: {
              required: true,
              email: true,
            },
          },
          {
            type: "select",
            name: "x509_alias",
            label: "Certificate Alias",
            // placeholder: "X.509 Certificate Alias on the KeyStore",
            _validate: {
              required: true,
            },
            list: [],
          },
        ],
      },
      fields: [
        {
          key: "key",
          label: "#",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "display",
          label: "Display",
          sortable: false,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "timestamp",
          label: "Timestamp",
          sortable: true,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "local",
          label: "Local Location",
          sortable: true,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "location",
          label: "Remote Location",
          sortable: true,
          class: "text-center",
          filter: true,
          export: true,
        },
        {
          key: "message",
          label: "Message ID",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "payload",
          label: "Payload",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "encryption",
          label: "Encryption",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "signature",
          label: "Signature",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "mdn",
          label: "MDN",
          sortable: true,
          class: "text-center",
          filter: false,
          export: true,
        },
        {
          key: "actions",
          label: "Actions",
          class: "text-center",
          export: false,
        },
      ],
      infoModal: {
        id: "info-modal",
        title: "",
      },
      items: [],
      items2: [
        {
          key: "0",
          display: "Display Ok",
          local: "PartnerB",
          location: "MyCompany",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "1",
          display: "Display Pending",
          local: "PartnerA",
          location: "MyCompany",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test.id",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "2",
          display: "Display Ok",
          local: "MyCompany",
          location: "PartnerB",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test.id",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "3",
          display: "Display Pending",
          local: "PartnerB",
          location: "PartnerA",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "tets",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "4",
          display: "Display Stop",
          local: "PartnerA",
          location: "PartnerB",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "5",
          display: "Display Ok",
          local: "PartnerB",
          location: "MyCompany",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "6",
          display: "Display Pending",
          local: "PartnerA",
          location: "MyCompany",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test.id",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "7",
          display: "Display Ok",
          local: "MyCompany",
          location: "PartnerB",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test.id",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "8",
          display: "Display Pending",
          local: "PartnerB",
          location: "PartnerA",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "tets",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "9",
          display: "Display Stop",
          local: "PartnerA",
          location: "PartnerB",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "10",
          display: "Display Ok",
          local: "PartnerB",
          location: "MyCompany",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "11",
          display: "Display Pending",
          local: "PartnerA",
          location: "MyCompany",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test.id",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "12",
          display: "Display Ok",
          local: "MyCompany",
          location: "PartnerB",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test.id",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "13",
          display: "Display Pending",
          local: "PartnerB",
          location: "PartnerA",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "tets",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
        {
          key: "14",
          display: "Display Stop",
          local: "PartnerA",
          location: "PartnerB",
          message:
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry.  ",
          timestamp: "2022/04/01 00:04:05",
          payload: "test",
          encryption: "SHA",
          signature: "SHA-256",
          mdn: "SYNC",
          actions: {
            show: { name: "Detail", show: true },
            delete: { name: "Delete", show: false },
            edit: { name: "Edit", show: false },
          },
        },
      ],

      item: {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      },
    };
  },
  components: {
    FormCustom,
    TableCustomMessage,
    FilterMessageTable,
  },
  computed: {
    isPathMessageList() {
      return this.$route.name == "Messages";
    },
    getFilterItems() {
      let _list = this.items2;
      if (this.filter.location != "") {
        _list = _.filter(_list, (item) => {
          return item.location == this.filter.location;
        });
      }
      if (this.filter.local != "") {
        _list = _.filter(_list, (item) => {
          return item.local == this.filter.local;
        });
      }
      if (this.filter.displays.length == 0) {
        _list = [];
      } else {
        let _list2 = [];
        this.filter.displays.forEach((display) => {
          let aux = _.filter(_list, (item) => {
            return item.display == display;
          });
          _list2 = _.concat(_list2, aux);
        });
        _list = _list2;
      }
      return _list;
    },
  },
  mounted: async function () {
    var list = await Utils.Crud.getList("cert");
    this.schema.fields[3].list = _.map(list, (item) => {
      return { value: item, text: item };
    });
    await this.getList();
  },
  methods: {
    exportExcel() {
      const fileName = "table-"+moment().format("YYYYMMDDH:mm:ss")+".xlsx";
       let _items = _.map(this.getFilterItems, (_item) => {
        let obj={}
        _.forIn(_item, function (value, key) {
            if(typeof _item[key]==='string'){
              obj[key]=value
            }
        });
        return obj
      });
      const ws = XLSX.utils.json_to_sheet(_items);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, "output");
      XLSX.writeFile(wb, fileName);
    },
    exportPdf() {
      const doc = new jsPDF();
      let head = _.map(
        _.filter(this.fields, (field) => field.export),
        (_field) => {
          return _field.label;
        }
      );

      let body = _.map(this.items2, (_item) => {
        let array=[]
        _.forIn(_item, function (value, key) {
            if(typeof _item[key]==='string'){
              array.push(value);
            }
        });
        return array
      });
      autoTable(doc, {
        head: [head],
        body: body
      });
      doc.save("table-"+moment().format("YYYYMMDDH:mm:ss")+".pdf");
      // doc.autoPrint(this.fields, this.items2);
      // doc.save("two-by-four.pdf");
    },
    filterItems: function (data) {
    },
    resetModal: function () {
      this.infoModal.title = "";
      this.item = {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      };
      this.$root.$emit("bv::hide::modal", this.infoModal.id);
    },
    deleteObject: async function (item) {
      try {
        Swal.fire({
          title: "Are you sure to delete?",
          html: `a partner  ${item.name}`,
          icon: "warning",
          showCancelButton: true,
          confirmButtonText: "Yes",
          cancelButtonText: "No",
        }).then(async (result) => {
          if (result.isConfirmed) {
            Utils.Crud.deleteObject("partner", item.key)
              .then((response) => {
                Swal.fire("Deleted!", "", "success");
                this.getList();
              })
              .catch((e) => {
                console.log("delete parner", e);
                Swal.fire("Error!", e, "error");
              });
          }
        });
      } catch (e) {
        console.log(e);
        Swal.fire("Error!", e, "error");
      }
    },
    newObject: function () {
      this.infoModal.title = "New partner";
      this.item = {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      };
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    editObject: async function (_item) {
      this.infoModal.title = "Message Edit";
      var newObj = this.getNewObject();
      const result = await Utils.Crud.getObject("partner", _item.key, newObj);
      this.item = Object.assign(this.item, result);
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    showObject: async function (_item) {
      this.$router.push({ name: "MessageDetail", params: { id: _item.key } });
    },
    getNewObject: function () {
      var obj = { _id: null };
      this.schema.fields.forEach((f) => (obj[f.name] = f.value));
      return obj;
    },
    getList: async function () {
      this.items = await Utils.Crud.getList("partner").then((results) => {
        return _.map(results, (item, index) => {
          return {
            key: index,
            name: item,
            actions: {
              show: { name: "Detail", show: true },
              delete: { name: "Delete", show: false },
              edit: { name: "Edit", show: false },
            },
          };
        });
      });
    },
    saveObject: async function (data) {
      try {
        await Utils.Crud.saveObject("partner", data);
        this.resetModal();
        await this.getList();
      } catch (e) {
        Swal.fire("Error!", e, "error");
      }
    },
  },
};
</script>
<style >
</style>