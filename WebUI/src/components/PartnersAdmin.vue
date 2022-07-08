<template>
  <div id="partners">
    <b-breadcrumb :items="breadcrumbs"></b-breadcrumb>
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
      <h1 class="h2">Partners</h1>
      <div class="btn-toolbar mb-2 mb-md-0">
        <div class="btn-group mr-2">
          <b-button
            v-if="isPathPartnerList"
            @click="newObject()"
            variant="success"
            size="sm"
            >New</b-button
          >
          <!-- <button type="button" class="btn btn-sm btn-outline-secondary">Export</button> -->
        </div>
      </div>
    </div>
    <table-custom
      :loading="loadingTable"
      :items="items"
      :fields="fields"
      @deleteObject="deleteObject"
      @editObject="editObject"
    ></table-custom>
    <b-modal hide-footer :id="infoModal.id" :title="infoModal.title" ok-only @hidden="resetModal" no-close-on-esc no-close-on-backdrop >
      <form-custom
        v-if="infoModal.title"
        title="Partner Editor"
        :schema="schema"
        :model="item"
        @commit="saveObject"
        @revert="resetModal"
        :loading="loadingForm"
      >
      </form-custom>
    </b-modal>
  </div>
</template>
<script>
import FormCustom from "./FormCustom";
import TableCustom from "./TableCustom";
import Utils from "../utils";
import Swal from "sweetalert2";
var _ = require("lodash");
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
          text: "Partners",
          to: { name: "Partners" },
          active: true,
        },
      ],
      schema: {
        fields: [
          {
            type: "text",
            name: "name",
            label: "Partner ID",
            placeholder: "Trading Partner's Unique Key Name",
            _validate: {
              required: true,
            },
          },
          {
            type: "text",
            name: "as2_id",
            label: "AS2 ID",
            placeholder: "Trading Partner's AS2 ID",
            _validate: {
              required: true,
            },
          },
          {
            type: "email",
            name: "email",
            label: "Email",
            placeholder: "Trading Partner's Email Address",
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
        },
        {
          key: "name",
          label: "Partner ID",
          sortable: true,
          class: "text-center",
        },
        { key: "actions", label: "Actions", class: "text-center" },
      ],
      infoModal: {
        id: "info-modal",
        title: "",
      },
      items: [],
      item: {
        _id: null,
        name: "",
        as2_id: "",
        email: "",
        x509_alias: "",
      },
      loadingTable: false,
      loadingForm: false,
    };
  },
  components: {
    FormCustom,
    TableCustom,
  },
  computed: {
    isPathPartnerList() {
      return this.$route.name == "Partners";
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
      this.loadingTable=false;
      this.loadingForm=false;

    },
    deleteObject: async function (item) {
      try {
        this.loadingTable = true;
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
                this.loadingTable = false;
                this.getList();
              })
              .catch((e) => {
                console.log("delete parner", e);
                Swal.fire("Error!", e, "error");
                this.loadingTable = false;
              });
          } else {
            this.loadingTable = false;
          }
        });
      } catch (e) {
        console.log(e);
        Swal.fire("Error!", e, "error");
        this.loadingTable = false;
      }
    },
    newObject: function () {
      this.loadingTable=true;
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
      this.loadingTable = true;
      this.infoModal.title = "Edit partner";
      var newObj = this.getNewObject();
      const result = await Utils.Crud.getObject("partner", _item.key, newObj);
      console.log("resutl", result);
      this.item = Object.assign(this.item, result);
      this.$root.$emit("bv::show::modal", this.infoModal.id);
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
              edit: { name: "Edit", show: true },
              delete: { name: "Delete", show: true },
            },
          };
        });
      });
    },
    saveObject: async function (data) {
      this.loadingForm = true;
      try {
        await Utils.Crud.saveObject("partner", data).then(() => {
          this.loadingForm = false;
          this.loadingTable = false;
        });
        this.resetModal();
        await this.getList();
      } catch (e) {
        Swal.fire("Error!", e, "error");
        this.loadingForm = false;
      }
    },
  },
};
</script>
<style >
</style>